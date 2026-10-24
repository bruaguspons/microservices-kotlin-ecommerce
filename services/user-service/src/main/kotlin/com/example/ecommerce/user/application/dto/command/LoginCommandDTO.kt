package com.example.ecommerce.user.application.dto.command

import jakarta.validation.constraints.NotBlank

/**
 * Login request payload for `POST /api/auth/login`.
 *
 * @param email the account's email
 * @param password the account's plaintext password
 */
data class LoginCommandDTO(
    @field:NotBlank
    val email: String?,
    @field:NotBlank
    val password: String?,
)
