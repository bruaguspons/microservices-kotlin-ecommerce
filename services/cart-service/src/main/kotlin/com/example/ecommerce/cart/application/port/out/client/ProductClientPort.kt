package com.example.ecommerce.cart.application.port.out.client

import com.example.ecommerce.cart.domain.valueobject.ProductId

/**
 * Outbound port for cross-service reads from product-service (see design:
 * Sync Communication (WebClient)). Analogous to a `*RepositoryPort`,
 * but implemented by an HTTP adapter instead of persistence.
 */
interface ProductClientPort {

    /**
     * @throws com.example.ecommerce.cart.application.exception.ProductNotFoundException if product-service responds 404
     * @throws com.example.ecommerce.cart.application.exception.ProductUnavailableException on timeout, connection failure, or open circuit breaker
     */
    fun getById(id: ProductId): ProductPriceResultDTO?
}
