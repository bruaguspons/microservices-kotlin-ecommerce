package com.example.ecommerce.buildingblocks.outbox

/**
 * Lifecycle states for an [OutboxMessageEntity] row (see design: Async
 * Communication / Outbox table + Publisher confirms state machine).
 */
enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER,
}
