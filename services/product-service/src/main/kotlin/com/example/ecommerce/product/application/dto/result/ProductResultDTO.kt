package com.example.ecommerce.product.application.dto.result

import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.model.Product
import java.math.BigDecimal
import java.util.UUID

data class ProductResultDTO(
    val id: UUID?,
    val name: String?,
    val desc: String?,
    val price: BigDecimal?,
    val imagePath: String?,
    val category: ProductCategory?,
    val version: Long?,
) {

    companion object {
        @JvmStatic
        fun from(product: Product): ProductResultDTO = ProductResultDTO(
            product.id!!.value,
            product.name,
            product.desc,
            product.price,
            product.imagePath,
            product.category,
            product.version,
        )
    }
}
