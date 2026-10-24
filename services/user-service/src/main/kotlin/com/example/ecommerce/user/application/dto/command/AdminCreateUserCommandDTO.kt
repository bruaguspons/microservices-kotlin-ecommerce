package com.example.ecommerce.user.application.dto.command

import com.example.ecommerce.user.domain.enums.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * ADMIN-only user-creation payload, used by `POST /api/admin/users`. Unlike
 * [RegisterUserCommandDTO], this DTO carries an explicit, arbitrary `role`
 * (including [UserRole.ADMIN]) - only an authenticated ADMIN may submit it.
 *
 * @param email the new account's email
 * @param firstname the new account's first name
 * @param lastname the new account's last name
 * @param age the new account's age; must satisfy [com.example.ecommerce.user.domain.model.User.MINIMUM_AGE]
 * @param password the new account's plaintext password, hashed before persistence
 * @param role the explicit role to assign to the new account
 */
data class AdminCreateUserCommandDTO(
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
    @field:NotNull
    val role: UserRole?,
)
