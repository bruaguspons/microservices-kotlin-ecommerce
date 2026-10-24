package com.example.ecommerce.cart.application.port.out.client

import java.math.BigDecimal
import java.util.UUID

/**
 * Result of a [ProductClientPort.getById] call - the subset of
 * product-service's `ProductResultDTO` cart-service needs to snapshot
 * a line item's price (see design: Sync Communication).
 */
data class ProductPriceResultDTO(
    val id: UUID?,
    val price: BigDecimal?,
    val version: Long,
)
