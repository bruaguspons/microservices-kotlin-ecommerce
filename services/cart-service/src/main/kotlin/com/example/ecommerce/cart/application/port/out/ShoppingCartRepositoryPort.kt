package com.example.ecommerce.cart.application.port.out

import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import java.util.Optional

interface ShoppingCartRepositoryPort {
    fun findById(id: ShoppingCartId): Optional<ShoppingCart>

    fun findAll(): List<ShoppingCart>

    /**
     * Loads every OPEN (unpaid) cart that contains a line item for
     * `productId`. Filtering on unpaid status is done at the JPQL
     * level (see design: Product Price-Change Propagation) so paid carts
     * are never even loaded - enforces the paid-cart-immutability invariant
     * at the persistence boundary, not just in memory.
     */
    fun findOpenCartsContainingProduct(productId: ProductId): List<ShoppingCart>

    fun save(shoppingCart: ShoppingCart): ShoppingCart

    fun deleteById(id: ShoppingCartId)
}
