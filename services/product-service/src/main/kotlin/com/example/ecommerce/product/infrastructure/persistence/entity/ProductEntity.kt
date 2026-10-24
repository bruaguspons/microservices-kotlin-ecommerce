package com.example.ecommerce.product.infrastructure.persistence.entity

import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.shared.infrastructure.persistance.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "products")
class ProductEntity(
    id: UUID? = null,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
    name: String? = null,
    desc: String? = null,
    price: BigDecimal? = null,
    imagePath: String? = null,
    category: ProductCategory? = null,
) : BaseEntity(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    @field:NotBlank
    @Column(nullable = false)
    var name: String? = name
        protected set

    @Column(nullable = false)
    var desc: String? = desc
        protected set

    @Column(nullable = false)
    var price: BigDecimal? = price
        protected set

    @Column(nullable = false)
    var imagePath: String? = imagePath
        protected set

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var category: ProductCategory? = category
        protected set
}
