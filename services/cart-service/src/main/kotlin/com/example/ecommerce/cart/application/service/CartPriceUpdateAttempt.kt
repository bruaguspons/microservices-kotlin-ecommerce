package com.example.ecommerce.cart.application.service

import com.example.ecommerce.cart.application.exception.ShoppingCartNotFoundException
import com.example.ecommerce.cart.application.port.out.ShoppingCartRepositoryPort
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Single price-update attempt for one cart, run in its own `REQUIRES_NEW`
 * transaction. Kept as a separate bean (not a private method on
 * [ShoppingCartServiceImpl]) so the `@Transactional` AOP proxy
 * actually intercepts each call - Spring AOP does not apply to same-class
 * (self-invoked) method calls (same pattern as
 * `ProductServiceHttpClient` for the WebClient adapters).
 *
 * Running each retry attempt in a fresh transaction avoids the JPA/Hibernate
 * anti-pattern of retrying a save against a persistence context that already
 * threw an [org.springframework.dao.OptimisticLockingFailureException]
 * during flush: once that exception occurs the surrounding EntityManager can
 * be left unusable, so a retry must start from a clean transaction/persistence
 * context, not reuse the failed one.
 */
@Component
class CartPriceUpdateAttempt(
    private val repository: ShoppingCartRepositoryPort,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun apply(cartId: ShoppingCartId, productId: ProductId, newPrice: BigDecimal?, version: Long) {
        val cart = repository.findById(cartId)
            .orElseThrow { ShoppingCartNotFoundException("Shopping cart not found with id: ${cartId.value}") }
        cart.updatePriceForProduct(productId, newPrice, version)
        repository.save(cart)
    }
}
