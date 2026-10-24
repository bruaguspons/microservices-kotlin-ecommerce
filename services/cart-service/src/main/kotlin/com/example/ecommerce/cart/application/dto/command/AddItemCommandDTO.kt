package com.example.ecommerce.cart.application.dto.command

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.UUID

data class AddItemCommandDTO(
    @field:NotNull
    val productId: UUID?,
    @field:NotNull
    @field:Positive
    val quantity: Int?,
)
