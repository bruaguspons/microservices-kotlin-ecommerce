package com.example.ecommerce.product.domain.model

import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.exception.InvalidPriceException
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.shared.domain.model.BaseModel
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class Product private constructor(
    id: ProductId?,
    createdAt: LocalDateTime?,
    createdBy: UUID?,
    lastModified: LocalDateTime?,
    lastModifiedBy: UUID?,
    version: Long?,
    isDeleted: Boolean,
    name: String?,
    desc: String?,
    price: BigDecimal?,
    imagePath: String?,
    category: ProductCategory?,
) : BaseModel<ProductId>(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    var name: String? = name
        private set

    var desc: String? = desc
        private set

    var price: BigDecimal? = price
        private set

    var imagePath: String? = imagePath
        private set

    var category: ProductCategory? = category
        private set

    companion object {

        @JvmStatic
        fun create(
            name: String?,
            desc: String?,
            price: BigDecimal?,
            imagePath: String?,
            category: ProductCategory?,
        ): Product {
            validatePriceStructure(price)
            validatePricePositive(price!!)
            return Product(
                ProductId.emptyValue(),
                null,
                null,
                null,
                null,
                null,
                false,
                name,
                desc,
                price,
                imagePath,
                category,
            )
        }

        @JvmStatic
        fun reconstitute(
            id: ProductId?,
            createdAt: LocalDateTime?,
            createdBy: UUID?,
            lastModified: LocalDateTime?,
            lastModifiedBy: UUID?,
            version: Long?,
            isDeleted: Boolean,
            name: String?,
            desc: String?,
            price: BigDecimal?,
            imagePath: String?,
            category: ProductCategory?,
        ): Product {
            validatePriceStructure(price)
            return Product(
                id,
                createdAt,
                createdBy,
                lastModified,
                lastModifiedBy,
                version,
                isDeleted,
                name,
                desc,
                price,
                imagePath,
                category,
            )
        }

        /**
         * Validates that the price is structurally present.
         *
         * @param price the price to validate
         * @throws IllegalArgumentException if `price` is `null`
         */
        private fun validatePriceStructure(price: BigDecimal?) {
            if (price == null) {
                throw IllegalArgumentException("Price cannot be null")
            }
        }

        /**
         * Validates that the price satisfies the business rule of being strictly positive.
         *
         * @param price the price to validate
         * @throws InvalidPriceException if `price` is zero or negative
         */
        private fun validatePricePositive(price: BigDecimal) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw InvalidPriceException("Price must be greater than zero, but was $price")
            }
        }
    }
}
