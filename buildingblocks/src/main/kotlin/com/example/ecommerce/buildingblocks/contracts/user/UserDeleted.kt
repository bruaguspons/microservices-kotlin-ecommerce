package com.example.ecommerce.buildingblocks.contracts.user

import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import java.time.Instant
import java.util.UUID

/**
 * Published by user-service (via `UserDeletedEventPublisher` ->
 * outbox) when a user is deleted. Consumed by: cart-service's
 * `UserDeletedListener` (log-and-skip minimal handling - see design:
 * Event Catalog).
 */
data class UserDeleted(
    val userId: UUID?,
    val deletedAt: Instant?,
) : DomainEvent {

    override fun eventType(): String = EventRoutingKeys.USER_DELETED
}
