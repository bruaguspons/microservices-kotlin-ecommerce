package com.example.ecommerce.buildingblocks.event

/**
 * Application-layer port for publishing a [DomainEvent]. Implementations
 * MUST write to the transactional outbox in the same `@Transactional`
 * boundary as the business state change that triggered the event - never
 * publish directly to the broker from application code (see design: Async
 * Communication / RabbitMQ + Outbox).
 */
interface EventDispatcher {

    fun publish(event: DomainEvent)
}
