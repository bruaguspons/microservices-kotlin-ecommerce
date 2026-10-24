package com.example.ecommerce.buildingblocks.event

/**
 * Dot-case routing key / outbox `event_type` constants for the four
 * events in the Event Catalog. Wire-level naming is always dot-case even
 * though the corresponding event classes use PascalCase (see spec:
 * Event naming consistency).
 */
object EventRoutingKeys {

    const val CART_PAID = "cart.paid"
    const val PRODUCT_PRICE_CHANGED = "product.price-changed"
    const val USER_REGISTERED = "user.registered"
    const val USER_DELETED = "user.deleted"
}
