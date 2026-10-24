package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.UnavailableException

/**
 * Thrown when `ProductClientPort` times out, cannot connect, or the
 * circuit breaker is open (see design: Sync Communication / Error mapping -
 * distinct from "doesn't exist" so callers can tell the two apart).
 */
class ProductUnavailableException(message: String?) : UnavailableException("PRODUCT_UNAVAILABLE", message)
