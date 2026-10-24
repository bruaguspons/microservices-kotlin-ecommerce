package com.example.ecommerce.buildingblocks.contracts.product

import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Published by product-service (via `ProductPriceChangedEventPublisher`
 * -> outbox) when a product's price changes. `version` is sourced
 * from the entity's JPA `@Version` column, used by consumers to
 * discard out-of-order deliveries. Consumed by:
 * cart-service's `ProductPriceChangedListener` (see design: Event
 * Catalog, Product Price-Change Propagation).
 */
data class ProductPriceChanged(
    val productId: UUID?,
    val newPrice: BigDecimal?,
    val version: Long,
    val changedAt: Instant?,
) : DomainEvent {

    override fun eventType(): String = EventRoutingKeys.PRODUCT_PRICE_CHANGED
}
