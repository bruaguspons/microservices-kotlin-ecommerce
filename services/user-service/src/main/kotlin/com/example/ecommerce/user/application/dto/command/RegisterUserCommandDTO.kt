package com.example.ecommerce.user.application.dto.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Public self-registration payload. Deliberately carries no `role` field: it is
 * physically impossible for an anonymous caller to submit a role. All accounts created via
 * this endpoint are forced to [com.example.ecommerce.user.domain.enums.UserRole.USER]
 * server-side.
 *
 * @param email the new account's email
 * @param firstname the new account's first name
 * @param lastname the new account's last name
 * @param age the new account's age; must satisfy [com.example.ecommerce.user.domain.model.User.MINIMUM_AGE]
 * @param password the new account's plaintext password, hashed before persistence
 */
data class RegisterUserCommandDTO(
    @field:NotBlank
    val email: String?,
    @field:NotBlank
    val firstname: String?,
    @field:NotBlank
    val lastname: String?,
    @field:NotNull
    val age: Int?,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    @field:Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")
    val password: String?,
)
