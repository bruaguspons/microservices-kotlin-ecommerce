package com.example.ecommerce.buildingblocks.outbox

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Wires publisher-confirm (`ConfirmCallback`) and unroutable-message
 * (`ReturnsCallback`) handling into the shared [RabbitTemplate] and owns
 * every outbox row state transition:
 *  - ACK without a returned message -> status=PUBLISHED, published_at=now()
 *  - ACK with a returned message (unroutable), NACK, or a send exception
 *    -> [recordFailure]: retry_count++ and next_attempt_at = now + backoff
 *  - failure that reaches [OutboxRetryPolicy.MAX_ATTEMPTS] -> DEAD_LETTER and
 *    an ERROR log, in the same transaction
 *
 * Callbacks run on the AMQP client thread, so each transition opens an
 * explicit transaction through a [TransactionTemplate]. Transitions apply only
 * while the row is still PENDING, so a late callback cannot overwrite a
 * terminal state.
 *
 * Note: for `returnedMessage` to fire, each service's
 * `spring.rabbitmq.publisher-returns=true`,
 * `spring.rabbitmq.template.mandatory=true` and
 * `publisher-confirm-type=correlated` must be set (service-level
 * application.yml wiring, out of scope for this shared module).
 */
@Component
class OutboxRabbitCallbacks(
    private val rabbitTemplate: RabbitTemplate,
    private val outboxMessageRepository: OutboxMessageRepository,
    transactionManager: PlatformTransactionManager,
) : RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    // Public (not internal): Kotlin `internal` would mangle the JVM method name.
    @PostConstruct
    fun wireCallbacks() {
        rabbitTemplate.setConfirmCallback(this)
        rabbitTemplate.setReturnsCallback(this)
    }

    override fun confirm(correlationData: CorrelationData?, ack: Boolean, cause: String?) {
        if (correlationData == null) {
            return
        }
        val messageId = UUID.fromString(correlationData.id)
        val returned = correlationData.returned
        when {
            !ack -> recordFailure(messageId, cause)
            returned != null -> recordFailure(messageId, "unroutable: ${returned.replyCode} ${returned.replyText}")
            else -> markPublished(messageId)
        }
    }

    // Only logs: the confirm callback carries the outcome via CorrelationData.returned.
    override fun returnedMessage(returned: ReturnedMessage) {
        log.warn(
            "Outbox message returned as unroutable: exchange={}, routingKey={}, replyCode={}, replyText={}",
            returned.exchange, returned.routingKey, returned.replyCode, returned.replyText,
        )
    }

    fun recordFailure(id: UUID, cause: String?) {
        inPendingTransaction(id) { message ->
            val attempts = message.retryCount + 1
            message.retryCount = attempts
            if (attempts >= OutboxRetryPolicy.MAX_ATTEMPTS) {
                message.status = OutboxStatus.DEAD_LETTER
                message.nextAttemptAt = null
                log.error(
                    "Outbox message dead-lettered: id={}, eventType={}, attempts={}, cause={}",
                    message.id, message.eventType, attempts, cause,
                )
            } else {
                message.nextAttemptAt = Instant.now().plus(OutboxRetryPolicy.backoffAfter(attempts))
            }
        }
    }

    private fun markPublished(id: UUID) {
        inPendingTransaction(id) { message ->
            message.status = OutboxStatus.PUBLISHED
            message.publishedAt = Instant.now()
        }
    }

    private fun inPendingTransaction(id: UUID, transition: (OutboxMessageEntity) -> Unit) {
        transactionTemplate.executeWithoutResult {
            outboxMessageRepository.findById(id)
                .filter { it.status == OutboxStatus.PENDING }
                .ifPresent { message ->
                    transition(message)
                    outboxMessageRepository.save(message)
                }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutboxRabbitCallbacks::class.java)
    }
}
