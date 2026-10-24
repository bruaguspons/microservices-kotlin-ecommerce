package com.example.ecommerce.cart.domain.model

import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ProductShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.shared.domain.model.BaseModel
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Line item of a [ShoppingCart], part of the cart aggregate's own
 * package - it is not a network boundary, it's the same service/transaction
 * as [ShoppingCart].
 */
class ProductShoppingCart private constructor(
    id: ProductShoppingCartId?,
    createdAt: LocalDateTime?,
    createdBy: UUID?,
    lastModified: LocalDateTime?,
    lastModifiedBy: UUID?,
    version: Long?,
    isDeleted: Boolean,
    productId: ProductId?,
    shoppingCartId: ShoppingCartId?,
    quantity: Int?,
    unitPrice: BigDecimal?,
    priceVersion: Long,
) : BaseModel<ProductShoppingCartId>(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    var productId: ProductId? = productId
        private set

    var shoppingCartId: ShoppingCartId? = shoppingCartId
        private set

    var quantity: Int? = quantity
        private set

    var unitPrice: BigDecimal? = unitPrice
        private set

    /**
     * Last-known version of the product's price, sourced from
     * `ProductPriceChanged.version` (itself the product's JPA
     * `@Version`). Used to discard out-of-order/stale price-change
     * events (see design: Product Price-Change Propagation).
     */
    var priceVersion: Long = priceVersion
        private set

    /**
     * Changes the quantity of this line item, applying full (create-level) validation.
     *
     * @param quantity the new quantity
     * @throws IllegalArgumentException if `quantity` is `null` or not positive
     */
    fun changeQuantity(quantity: Int?) {
        validateQuantityStructure(quantity)
        validateQuantityPositive(quantity!!)
        this.quantity = quantity
    }

    /**
     * Applies a new price snapshot for this line item, but only if
     * `incomingVersion` is not older than the currently stored
     * [priceVersion] - discards stale/out-of-order price-change
     * events silently (see design: idempotency note).
     *
     * @param newPrice the new unit price
     * @param incomingVersion the version carried by the incoming price-change event
     * @return `true` if the price was applied, `false` if the event was stale and skipped
     */
    internal fun applyPriceIfNewer(newPrice: BigDecimal?, incomingVersion: Long): Boolean {
        if (incomingVersion < this.priceVersion) {
            return false
        }
        this.unitPrice = newPrice
        this.priceVersion = incomingVersion
        return true
    }

    companion object {

        @JvmStatic
        fun create(
            productId: ProductId?,
            shoppingCartId: ShoppingCartId?,
            quantity: Int?,
            unitPrice: BigDecimal?,
            priceVersion: Long,
        ): ProductShoppingCart {
            validateQuantityStructure(quantity)
            validateQuantityPositive(quantity!!)
            validateUnitPrice(unitPrice)
            return ProductShoppingCart(
                ProductShoppingCartId.emptyValue(),
                null,
                null,
                null,
                null,
                null,
                false,
                productId,
                shoppingCartId,
                quantity,
                unitPrice,
                priceVersion,
            )
        }

        @JvmStatic
        fun reconstitute(
            id: ProductShoppingCartId?,
            createdAt: LocalDateTime?,
            createdBy: UUID?,
            lastModified: LocalDateTime?,
            lastModifiedBy: UUID?,
            version: Long?,
            isDeleted: Boolean,
            productId: ProductId?,
            shoppingCartId: ShoppingCartId?,
            quantity: Int?,
            unitPrice: BigDecimal?,
            priceVersion: Long,
        ): ProductShoppingCart {
            validateQuantityStructure(quantity)
            validateUnitPrice(unitPrice)
            return ProductShoppingCart(
                id,
                createdAt,
                createdBy,
                lastModified,
                lastModifiedBy,
                version,
                isDeleted,
                productId,
                shoppingCartId,
                quantity,
                unitPrice,
                priceVersion,
            )
        }

        private fun validateQuantityStructure(quantity: Int?) {
            if (quantity == null) {
                throw IllegalArgumentException("Quantity cannot be null")
            }
        }

        private fun validateQuantityPositive(quantity: Int) {
            if (quantity <= 0) {
                throw IllegalArgumentException("Quantity must be greater than zero, but was $quantity")
            }
        }

        private fun validateUnitPrice(unitPrice: BigDecimal?) {
            if (unitPrice == null) {
                throw IllegalArgumentException("Unit price cannot be null")
            }
        }
    }
}
