package com.example.ecommerce.buildingblocks.contracts.cart

import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Published by cart-service (via `CartPaidEventPublisher` -> outbox)
 * when `ShoppingCart.pay()` succeeds. Consumed by: none in initial
 * scope (see design: Event Catalog).
 */
data class CartPaid(
    val cartId: UUID?,
    val userId: UUID?,
    val totalAmount: BigDecimal?,
    val paidAt: Instant?,
) : DomainEvent {

    override fun eventType(): String = EventRoutingKeys.CART_PAID
}
