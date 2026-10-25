package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.application.exception.UserUnavailableException
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.valueobject.UserId
import org.springframework.stereotype.Component

/**
 * Implements [UserClientPort] - same Error mapping pattern as
 * [ProductWebClientAdapter] (see design: Sync Communication). A 404 from
 * user-service is reported as `false`, not as an exception.
 */
@Component
class UserWebClientAdapter(
    private val httpClient: UserServiceHttpClient,
) : UserClientPort {

    override fun exists(id: UserId): Boolean {
        try {
            return httpClient.exists(id)
        } catch (ex: RuntimeException) {
            // Covers CallNotPermittedException, WebClientRequestException, any other
            // WebClientResponseException and generic timeouts: all map to "unavailable".
            throw UserUnavailableException("user-service is unavailable: ${ex.message}")
        }
    }
}
