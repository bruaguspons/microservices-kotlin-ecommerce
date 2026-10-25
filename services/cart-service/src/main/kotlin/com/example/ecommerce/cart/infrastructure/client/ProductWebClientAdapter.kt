package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.application.exception.ProductNotFoundException
import com.example.ecommerce.cart.application.exception.ProductUnavailableException
import com.example.ecommerce.cart.application.port.out.client.ProductClientPort
import com.example.ecommerce.cart.application.port.out.client.ProductPriceResultDTO
import com.example.ecommerce.cart.domain.valueobject.ProductId
import org.springframework.stereotype.Component

/**
 * Implements [ProductClientPort] - maps low-level HTTP/resilience
 * failures to the domain-facing exceptions per design's Error mapping:
 * 404 -> [ProductNotFoundException] (translated by [ProductServiceHttpClient]); timeout, connection failure, or
 * open circuit breaker -> [ProductUnavailableException]. No silent
 * fallback - every failure path throws.
 */
@Component
class ProductWebClientAdapter(
    private val httpClient: ProductServiceHttpClient,
) : ProductClientPort {

    override fun getById(id: ProductId): ProductPriceResultDTO? {
        try {
            return httpClient.fetch(id)
        } catch (ex: ProductNotFoundException) {
            // Already translated (and ignored by the circuit breaker) in ProductServiceHttpClient.
            throw ex
        } catch (ex: RuntimeException) {
            // Covers CallNotPermittedException (open circuit breaker), WebClientRequestException,
            // any other WebClientResponseException, and reactor-netty
            // ReadTimeoutException/connect-timeout wrapped as generic RuntimeException by
            // WebClient's blocking bridge: all map to "unavailable".
            throw ProductUnavailableException("product-service is unavailable: ${ex.message}")
        }
    }
}
