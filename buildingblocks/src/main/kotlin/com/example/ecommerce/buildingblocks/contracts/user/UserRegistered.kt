package com.example.ecommerce.buildingblocks.contracts.user

import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import java.time.Instant
import java.util.UUID

/**
 * Published by user-service (via `UserRegisteredEventPublisher` ->
 * outbox) when a new user is created. Consumed by: none in initial scope
 * (see design: Event Catalog).
 */
data class UserRegistered(
    val userId: UUID?,
    val email: String?,
    val registeredAt: Instant?,
) : DomainEvent {

    override fun eventType(): String = EventRoutingKeys.USER_REGISTERED
}
