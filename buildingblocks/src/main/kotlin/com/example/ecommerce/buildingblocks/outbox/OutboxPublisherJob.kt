package com.example.ecommerce.buildingblocks.outbox

import com.example.ecommerce.buildingblocks.config.RabbitMqTopologyConfig
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.Limit
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Polls `outbox_message` for PENDING rows and publishes them to
 * RabbitMQ with publisher confirms. The poll first claims a batch in its own
 * committed transaction (row lock + a lease written to `next_attempt_at`), then
 * sends outside any transaction so a slow broker never holds row locks and a
 * confirm can never race the claim commit. Terminal state transitions
 * (PUBLISHED / DEAD_LETTER) happen in [OutboxRabbitCallbacks], driven by the
 * broker's async ack/nack; this job only reports send exceptions to it.
 *
 * Deliberately not `@Transactional`: the claim transaction must commit before
 * the first send.
 */
@Component
class OutboxPublisherJob(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val rabbitTemplate: RabbitTemplate,
    private val callbacks: OutboxRabbitCallbacks,
    transactionManager: PlatformTransactionManager,
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Scheduled(fixedDelay = 5000)
    fun publishPending() {
        val batch = claimBatch()

        for (message in batch) {
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMqTopologyConfig.EVENTS_EXCHANGE,
                    message.eventType!!,
                    message.payload!!,
                    CorrelationData(message.id!!.toString()),
                )
            } catch (e: Exception) {
                callbacks.recordFailure(message.id!!, "send failed: ${e.message}")
            }
        }
    }

    private fun claimBatch(): List<OutboxMessageEntity> =
        transactionTemplate.execute {
            val now = Instant.now()
            val claimed = outboxMessageRepository.findClaimable(
                OutboxStatus.PENDING, OutboxRetryPolicy.MAX_ATTEMPTS, now, Limit.of(OutboxRetryPolicy.BATCH_SIZE),
            )
            val leaseUntil = now.plus(OutboxRetryPolicy.LEASE)
            claimed.forEach { it.nextAttemptAt = leaseUntil }
            outboxMessageRepository.saveAll(claimed)
            claimed
        } ?: emptyList()
}
