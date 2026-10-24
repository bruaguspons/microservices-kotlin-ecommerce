package com.example.ecommerce.buildingblocks.outbox

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Wires publisher-confirm (`ConfirmCallback`) and unroutable-message
 * (`ReturnsCallback`) handling into the shared [RabbitTemplate],
 * implementing the exact state machine from the design:
 *  - ACK -> status=PUBLISHED, published_at=now()
 *  - NACK/return -> retry_count++, status stays PENDING
 *  - 5th failure (retry_count reaches 5) -> status=DEAD_LETTER, ERROR log,
 *    atomic with that same callback invocation (never left at
 *    retry_count=5/status=PENDING waiting for the next poll)
 *
 * Note: for `returnedMessage` to fire, each service's
 * `spring.rabbitmq.publisher-returns=true` and
 * `publisher-confirm-type=correlated` must be set (service-level
 * application.yml wiring, out of scope for this shared module).
 */
@Component
class OutboxRabbitCallbacks(
    private val rabbitTemplate: RabbitTemplate,
    private val outboxMessageRepository: OutboxMessageRepository,
) : RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    // Public (not internal): Kotlin `internal` would mangle the JVM method name.
    @PostConstruct
    fun wireCallbacks() {
        rabbitTemplate.setConfirmCallback(this)
        rabbitTemplate.setReturnsCallback(this)
    }

    @Transactional
    override fun confirm(correlationData: CorrelationData?, ack: Boolean, cause: String?) {
        if (correlationData == null) {
            return
        }
        val messageId = UUID.fromString(correlationData.id)
        if (ack) {
            markPublished(messageId)
        } else {
            registerFailure(messageId, cause)
        }
    }

    @Transactional
    override fun returnedMessage(returned: ReturnedMessage) {
        val correlationId = returned.message.messageProperties.correlationId
        if (correlationId == null) {
            log.error(
                "Outbox message returned as unroutable without a correlation id: exchange={}, routingKey={}, replyText={}",
                returned.exchange, returned.routingKey, returned.replyText,
            )
            return
        }
        registerFailure(UUID.fromString(correlationId), "unroutable: " + returned.replyText)
    }

    private fun markPublished(id: UUID) {
        outboxMessageRepository.findById(id).ifPresent { message ->
            message.status = OutboxStatus.PUBLISHED
            message.publishedAt = Instant.now()
            outboxMessageRepository.save(message)
        }
    }

    private fun registerFailure(id: UUID, cause: String?) {
        outboxMessageRepository.findById(id).ifPresent { message ->
            val retries = message.retryCount + 1
            message.retryCount = retries
            if (retries >= MAX_RETRIES) {
                message.status = OutboxStatus.DEAD_LETTER
                log.error("Outbox message {} reached DEAD_LETTER status after {} retries. Cause: {}", id, retries, cause)
            }
            outboxMessageRepository.save(message)
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
        private val log = LoggerFactory.getLogger(OutboxRabbitCallbacks::class.java)
    }
}
