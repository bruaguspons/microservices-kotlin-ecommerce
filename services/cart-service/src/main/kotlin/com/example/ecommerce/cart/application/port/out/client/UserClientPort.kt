package com.example.ecommerce.cart.application.port.out.client

import com.example.ecommerce.cart.domain.valueobject.UserId

/**
 * Outbound port for cross-service reads from user-service (see design:
 * Sync Communication (WebClient)).
 */
interface UserClientPort {

    /**
     * @throws com.example.ecommerce.cart.application.exception.UserNotFoundException if user-service responds 404
     * @throws com.example.ecommerce.cart.application.exception.UserUnavailableException on timeout, connection failure, or open circuit breaker
     */
    fun exists(id: UserId): Boolean
}
