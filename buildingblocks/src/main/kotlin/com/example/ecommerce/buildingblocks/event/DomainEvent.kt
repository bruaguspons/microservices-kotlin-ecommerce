package com.example.ecommerce.buildingblocks.event

/**
 * Marker for a publishable domain event. [eventType] MUST return the
 * dot-case wire name used both as the outbox `event_type` column and
 * the RabbitMQ routing key (see spec: Event naming consistency).
 */
interface DomainEvent {

    fun eventType(): String
}
