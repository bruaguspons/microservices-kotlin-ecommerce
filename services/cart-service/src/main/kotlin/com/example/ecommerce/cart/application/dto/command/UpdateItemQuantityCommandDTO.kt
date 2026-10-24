package com.example.ecommerce.cart.application.dto.command

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class UpdateItemQuantityCommandDTO(
    @field:NotNull
    @field:Positive
    val quantity: Int?,
)
