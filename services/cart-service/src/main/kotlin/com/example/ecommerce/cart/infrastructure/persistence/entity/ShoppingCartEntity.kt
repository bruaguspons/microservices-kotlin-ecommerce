package com.example.ecommerce.cart.infrastructure.persistence.entity

import com.example.ecommerce.buildingblocks.persistence.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Database-per-service: `userId` is now a plain UUID column, NOT a
 * `@ManyToOne` to a `UserEntity` - user-service owns its own
 * schema, there is no cross-DB JPA relation anymore (see design:
 * Database-per-Service).
 */
@Entity
@Table(name = "shopping_cart")
@SQLDelete(sql = "UPDATE shopping_cart SET is_deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = false")
class ShoppingCartEntity(
    id: UUID? = null,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
    total: BigDecimal? = null,
    payed: Boolean = false,
    userId: UUID? = null,
    items: MutableList<ProductShoppingCartEntity>? = null,
) : BaseEntity(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    @Column(nullable = false)
    var total: BigDecimal? = total
        protected set

    @Column(nullable = false)
    var payed: Boolean = payed
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = userId
        protected set

    @OneToMany(mappedBy = "shoppingCart", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<ProductShoppingCartEntity>? = items
        protected set
}
