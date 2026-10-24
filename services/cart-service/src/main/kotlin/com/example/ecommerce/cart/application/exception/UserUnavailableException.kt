package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.UnavailableException

/**
 * Thrown when `UserClientPort` times out, cannot connect, or the
 * circuit breaker is open (see design: Sync Communication / Error mapping).
 */
class UserUnavailableException(message: String?) : UnavailableException("USER_UNAVAILABLE", message)
