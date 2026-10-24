package com.example.ecommerce.product.application.dto.command

import com.example.ecommerce.product.domain.enums.ProductCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class UpdateProductCommandDTO(
    @field:NotBlank
    val name: String?,
    val desc: String?,
    @field:NotNull
    @field:Positive
    val price: BigDecimal?,
    val imagePath: String?,
    @field:NotNull
    val category: ProductCategory?,
)
