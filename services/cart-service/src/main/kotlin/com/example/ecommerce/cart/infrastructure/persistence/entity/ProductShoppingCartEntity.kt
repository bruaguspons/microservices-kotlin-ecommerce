package com.example.ecommerce.cart.infrastructure.persistence.entity

import com.example.ecommerce.buildingblocks.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Database-per-service: `productId` is a plain UUID column, NOT a
 * `@ManyToOne` to a `ProductEntity` - no FK, no cross-DB join
 * (see design: Database-per-Service). `unitPrice` is a value-copy
 * snapshot fetched via `ProductClientPort` at add-time; `priceVersion`
 * tracks the last-applied `ProductPriceChanged.version`
 * for idempotent, out-of-order-safe price updates.
 */
@Entity
@Table(name = "product_shopping_cart")
@SQLDelete(sql = "UPDATE product_shopping_cart SET is_deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = false")
class ProductShoppingCartEntity(
    id: UUID? = null,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
    shoppingCart: ShoppingCartEntity? = null,
    productId: UUID? = null,
    quantity: Int? = null,
    unitPrice: BigDecimal? = null,
    priceVersion: Long = 0L,
) : BaseEntity(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_cart_id", nullable = false)
    var shoppingCart: ShoppingCartEntity? = shoppingCart
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: UUID? = productId
        protected set

    @Column(nullable = false)
    var quantity: Int? = quantity
        protected set

    @Column(nullable = false)
    var unitPrice: BigDecimal? = unitPrice
        protected set

    @Column(nullable = false)
    var priceVersion: Long = priceVersion
        protected set
}
