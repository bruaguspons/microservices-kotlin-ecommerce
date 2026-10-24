package com.example.ecommerce.buildingblocks.outbox

import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventDispatcher
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * [EventDispatcher] implementation backed by the transactional outbox.
 * Callers MUST invoke [publish] from within the same
 * `@Transactional` boundary as the business state change so the outbox
 * row commits atomically with it (see design: Payment Flow / transactional
 * outbox).
 */
@Component
class OutboxEventDispatcher(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val objectMapper: ObjectMapper,
) : EventDispatcher {

    override fun publish(event: DomainEvent) {
        try {
            val payload = objectMapper.writeValueAsString(event)
            val message = OutboxMessageEntity(
                eventType = event.eventType(),
                payload = payload,
                status = OutboxStatus.PENDING,
                retryCount = 0,
                createdAt = Instant.now(),
            )
            outboxMessageRepository.save(message)
        } catch (e: JacksonException) {
            throw IllegalStateException("Failed to serialize domain event " + event.eventType(), e)
        }
    }
}
