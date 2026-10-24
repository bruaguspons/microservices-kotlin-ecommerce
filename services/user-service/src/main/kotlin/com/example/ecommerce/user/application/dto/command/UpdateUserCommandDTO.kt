package com.example.ecommerce.user.application.dto.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpdateUserCommandDTO(
    @field:NotBlank
    val email: String?,
    @field:NotBlank
    val firstname: String?,
    @field:NotBlank
    val lastname: String?,
    @field:NotNull
    val age: Int?,
)
