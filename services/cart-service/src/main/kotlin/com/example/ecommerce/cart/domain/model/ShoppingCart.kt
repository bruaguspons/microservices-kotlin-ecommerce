package com.example.ecommerce.cart.domain.model

import com.example.ecommerce.cart.domain.exception.CartAlreadyPaidException
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ProductShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.buildingblocks.domain.BaseModel
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.UUID

/**
 * Aggregate root for the shopping cart - line items ([ProductShoppingCart])
 * are internal to this aggregate, not a separate network boundary.
 */
class ShoppingCart private constructor(
    id: ShoppingCartId?,
    createdAt: LocalDateTime?,
    createdBy: UUID?,
    lastModified: LocalDateTime?,
    lastModifiedBy: UUID?,
    version: Long?,
    isDeleted: Boolean,
    total: BigDecimal?,
    payed: Boolean,
    userId: UserId?,
    items: MutableList<ProductShoppingCart>?,
) : BaseModel<ShoppingCartId>(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    var total: BigDecimal? = total
        private set

    var payed: Boolean = payed
        private set

    var userId: UserId? = userId
        private set

    var items: MutableList<ProductShoppingCart>? = items
        private set

    /**
     * Adds a new line item to this cart and recalculates the total.
     *
     * @param productId the product being added
     * @param quantity the quantity of the product
     * @param unitPrice the unit price snapshot to capture for this line item (fetched via
     *                  `ProductClientPort` at the application layer)
     * @param priceVersion the product's price version at snapshot time (fetched via
     *                     `ProductClientPort`), used for later idempotent price-change updates
     * @return the newly created line item
     * @throws CartAlreadyPaidException if the cart has already been paid
     */
    fun addItem(productId: ProductId?, quantity: Int?, unitPrice: BigDecimal?, priceVersion: Long): ProductShoppingCart {
        validateNotPayed()
        val item = ProductShoppingCart.create(productId, this.id, quantity, unitPrice, priceVersion)
        items!!.add(item)
        recalculateTotal()
        return item
    }

    /**
     * Removes a line item from this cart and recalculates the total.
     *
     * @param itemId the identifier of the line item to remove
     * @throws CartAlreadyPaidException if the cart has already been paid
     * @throws IllegalArgumentException if no line item with `itemId` exists in this cart
     */
    fun removeItem(itemId: ProductShoppingCartId?) {
        validateNotPayed()
        val removed = items!!.removeIf { item -> item.id == itemId }
        if (!removed) {
            throw IllegalArgumentException("Item not found in cart with id: $itemId")
        }
        recalculateTotal()
    }

    /**
     * Updates the quantity of an existing line item and recalculates the total.
     *
     * @param itemId the identifier of the line item to update
     * @param quantity the new quantity
     * @return the updated line item
     * @throws CartAlreadyPaidException if the cart has already been paid
     * @throws IllegalArgumentException if no line item with `itemId` exists in this cart
     */
    fun updateItemQuantity(itemId: ProductShoppingCartId?, quantity: Int?): ProductShoppingCart {
        validateNotPayed()
        val item = items!!.firstOrNull { i -> i.id == itemId }
            ?: throw IllegalArgumentException("Item not found in cart with id: $itemId")
        item.changeQuantity(quantity)
        recalculateTotal()
        return item
    }

    /**
     * Marks this cart as paid.
     *
     * @throws CartAlreadyPaidException if the cart has already been paid
     */
    fun pay() {
        validateNotPayed()
        this.payed = true
    }

    /**
     * Applies a price update coming from a `product.price-changed` event
     * to every line item referencing `productId`, then recalculates the
     * total via the SAME routine [addItem] / [removeItem] use
     * (see design: Product Price-Change Propagation - no duplicated total
     * math). Defense-in-depth: throws if this cart is already paid even
     * though the repository query that loads candidate carts already filters
     * on open/unpaid status - an aggregate must never trust its caller
     * blindly for a mutation.
     *
     * Silently skips (no exception) if the incoming version is older than
     * a line item's stored version - stale/out-of-order event, naturally
     * idempotent on redelivery of the same version.
     *
     * @param productId the product whose price changed
     * @param newPrice the new unit price
     * @param incomingVersion the version carried by the incoming event
     * @throws CartAlreadyPaidException if the cart has already been paid
     */
    fun updatePriceForProduct(productId: ProductId?, newPrice: BigDecimal?, incomingVersion: Long) {
        validateNotPayed()
        var anyApplied = false
        for (item in items!!) {
            if (item.productId!! == productId) {
                anyApplied = anyApplied or item.applyPriceIfNewer(newPrice, incomingVersion)
            }
        }
        if (anyApplied) {
            recalculateTotal()
        }
    }

    private fun validateNotPayed() {
        if (payed) {
            throw CartAlreadyPaidException("Cannot modify a shopping cart that has already been paid")
        }
    }

    /**
     * Recalculates the total from the current line items, deriving it as
     * `sum(quantity * unitPrice)`. Never settable directly by callers.
     * The single reuse point for [addItem], [removeItem], and
     * [updatePriceForProduct].
     */
    private fun recalculateTotal() {
        this.total = items!!
            .map { item -> item.unitPrice!!.multiply(BigDecimal.valueOf(item.quantity!!.toLong())) }
            .fold(BigDecimal.ZERO) { acc, price -> acc.add(price) }
    }

    companion object {

        @JvmStatic
        fun create(userId: UserId?): ShoppingCart {
            validateUserId(userId)
            return ShoppingCart(
                ShoppingCartId.emptyValue(),
                null,
                null,
                null,
                null,
                null,
                false,
                BigDecimal.ZERO,
                false,
                userId,
                LinkedList(),
            )
        }

        @JvmStatic
        fun reconstitute(
            id: ShoppingCartId?,
            createdAt: LocalDateTime?,
            createdBy: UUID?,
            lastModified: LocalDateTime?,
            lastModifiedBy: UUID?,
            version: Long?,
            isDeleted: Boolean,
            total: BigDecimal?,
            payed: Boolean,
            userId: UserId?,
            items: MutableList<ProductShoppingCart>?,
        ): ShoppingCart {
            validateUserId(userId)
            return ShoppingCart(
                id,
                createdAt,
                createdBy,
                lastModified,
                lastModifiedBy,
                version,
                isDeleted,
                total,
                payed,
                userId,
                items,
            )
        }

        private fun validateUserId(userId: UserId?) {
            if (userId == null) {
                throw IllegalArgumentException("UserId cannot be null")
            }
        }
    }
}
