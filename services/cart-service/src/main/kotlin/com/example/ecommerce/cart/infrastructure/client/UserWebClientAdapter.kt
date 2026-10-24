package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.application.exception.UserNotFoundException
import com.example.ecommerce.cart.application.exception.UserUnavailableException
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.valueobject.UserId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Implements [UserClientPort] - same Error mapping pattern as
 * [ProductWebClientAdapter] (see design: Sync Communication).
 */
@Component
class UserWebClientAdapter(
    private val httpClient: UserServiceHttpClient,
) : UserClientPort {

    override fun exists(id: UserId): Boolean {
        try {
            return httpClient.exists(id)
        } catch (ex: WebClientResponseException.NotFound) {
            throw UserNotFoundException("User not found with id: ${id.value}")
        } catch (ex: RuntimeException) {
            // Covers CallNotPermittedException, WebClientRequestException, any other
            // WebClientResponseException and generic timeouts: all map to "unavailable".
            throw UserUnavailableException("user-service is unavailable: ${ex.message}")
        }
    }
}
