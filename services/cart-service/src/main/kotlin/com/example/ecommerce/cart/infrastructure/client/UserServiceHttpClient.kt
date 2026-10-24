package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.domain.valueobject.UserId
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * Low-level Resilience4j-wrapped HTTP call to user-service. Separate bean
 * from [UserWebClientAdapter] for the same self-invocation-proxy
 * reason documented on [ProductServiceHttpClient].
 *
 * Uses the `user-service` Resilience4j instance (same config shape
 * as `product-service`, see application.yml).
 */
@Component
class UserServiceHttpClient(
    private val userServiceWebClient: WebClient,
) {

    @CircuitBreaker(name = "user-service")
    @Retry(name = "user-service")
    fun exists(id: UserId): Boolean {
        val authHeader = CurrentRequestAuth.header()
        userServiceWebClient.get()
            .uri("/api/users/{id}", id.value)
            .headers { headers ->
                if (authHeader != null) {
                    headers.set(HttpHeaders.AUTHORIZATION, authHeader)
                }
            }
            .retrieve()
            .toBodilessEntity()
            .block()
        return true
    }
}
