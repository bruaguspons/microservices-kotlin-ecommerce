package com.example.ecommerce.buildingblocks.outbox

import com.example.ecommerce.buildingblocks.config.RabbitMqTopologyConfig
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Polls `outbox_message` for PENDING rows and publishes them to
 * RabbitMQ with publisher confirms. Terminal state transitions (PUBLISHED /
 * DEAD_LETTER) happen in [OutboxRabbitCallbacks], driven by the
 * broker's async ack/nack - this job only sends (see design: Async
 * Communication / Publisher confirms and state machine).
 */
@Component
class OutboxPublisherJob(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val rabbitTemplate: RabbitTemplate,
) {

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun publishPending() {
        val batch = outboxMessageRepository
            .findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, MAX_RETRIES)

        for (message in batch) {
            val correlationData = CorrelationData(message.id!!.toString())
            rabbitTemplate.convertAndSend(
                RabbitMqTopologyConfig.EVENTS_EXCHANGE,
                message.eventType!!,
                message.payload!!,
                correlationData,
            )
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
