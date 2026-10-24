package com.example.ecommerce.cart.application.port.`in`

import com.example.ecommerce.cart.application.dto.command.AddItemCommandDTO
import com.example.ecommerce.cart.application.dto.command.CreateShoppingCartDTO
import com.example.ecommerce.cart.application.dto.command.UpdateItemQuantityCommandDTO
import com.example.ecommerce.cart.application.dto.result.ProductShoppingCartResultDTO
import com.example.ecommerce.cart.application.dto.result.ShoppingCartResultDTO
import java.math.BigDecimal
import java.util.UUID

interface ShoppingCartServicePort {
    fun findById(id: UUID?): ShoppingCartResultDTO

    fun findAll(): List<ShoppingCartResultDTO>

    fun create(cmd: CreateShoppingCartDTO): ShoppingCartResultDTO

    fun deleteById(id: UUID?)

    /**
     * Marks the cart as paid (NEW use case, see design: Payment Flow) and
     * publishes `cart.paid` via outbox in the same transaction.
     */
    fun pay(id: UUID?): ShoppingCartResultDTO

    fun addItem(cartId: UUID?, cmd: AddItemCommandDTO): ProductShoppingCartResultDTO

    fun updateItemQuantity(cartId: UUID?, itemId: UUID?, cmd: UpdateItemQuantityCommandDTO): ProductShoppingCartResultDTO

    fun removeItem(cartId: UUID?, itemId: UUID?)

    /**
     * Applies a `product.price-changed` event to every OPEN cart
     * containing the product (see design: Product Price-Change Propagation).
     * Called from `ProductPriceChangedListener`; contains all the
     * business logic, the listener itself only deserializes and delegates.
     */
    fun handleProductPriceChanged(productId: UUID?, newPrice: BigDecimal?, version: Long)

    /**
     * Minimal (TODO-level, per design) handling of a `user.deleted`
     * event: logs receipt and does not crash. A full archive/soft-delete
     * feature for that user's carts is explicitly NOT required for initial
     * scope.
     */
    fun handleUserDeleted(userId: UUID?)
}
