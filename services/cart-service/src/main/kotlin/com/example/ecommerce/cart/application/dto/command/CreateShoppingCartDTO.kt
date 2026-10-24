package com.example.ecommerce.cart.application.dto.command

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateShoppingCartDTO(
    @field:NotNull
    val userId: UUID?,
)
