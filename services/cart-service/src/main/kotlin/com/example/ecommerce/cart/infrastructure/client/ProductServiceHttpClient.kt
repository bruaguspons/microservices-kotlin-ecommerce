package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.application.port.out.client.ProductPriceResultDTO
import com.example.ecommerce.cart.domain.valueobject.ProductId
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.util.UUID

/**
 * Low-level Resilience4j-wrapped HTTP call to product-service. Kept as a
 * separate bean (not a private method on [ProductWebClientAdapter]) so
 * the `@CircuitBreaker`/`@Retry` AOP proxies actually intercept
 * the call - Spring AOP does not apply to same-class (self-invoked) method
 * calls.
 *
 * `product-service` Resilience4j instance config (application.yml):
 * circuit breaker trips after 5 consecutive failures or 50% failure rate over
 * a 10-call window, 30s wait in open state before half-open; retry 3 attempts
 * total with exponential backoff. 404s are configured as
 * `ignore-exceptions` so a "doesn't exist" response is never retried
 * and never counts as a circuit-breaker failure (see design: Error mapping).
 */
@Component
class ProductServiceHttpClient(
    private val productServiceWebClient: WebClient,
) {

    @CircuitBreaker(name = "product-service")
    @Retry(name = "product-service")
    fun fetch(id: ProductId): ProductPriceResultDTO? {
        val authHeader = CurrentRequestAuth.header()
        return productServiceWebClient.get()
            .uri("/api/products/{id}", id.value)
            .headers { headers ->
                if (authHeader != null) {
                    headers.set(HttpHeaders.AUTHORIZATION, authHeader)
                }
            }
            .retrieve()
            .bodyToMono(RawProductResponse::class.java)
            .map { r -> ProductPriceResultDTO(r.id, r.price, r.version ?: 0L) }
            .block()
    }

    private data class RawProductResponse(
        val id: UUID?,
        val name: String?,
        val desc: String?,
        val price: BigDecimal?,
        val imagePath: String?,
        val category: String?,
        val version: Long?,
    )
}
