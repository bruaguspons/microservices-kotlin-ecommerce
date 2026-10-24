package com.example.ecommerce.user.application.dto.command

import com.example.ecommerce.user.domain.enums.UserRole
import jakarta.validation.constraints.NotNull

/**
 * ADMIN-only role-change payload, used by `PATCH /api/admin/users/{id}/role`. Mutates
 * exactly one field of an existing user; distinct from [UpdateUserCommandDTO]'s
 * full-resource-replace semantics.
 *
 * @param role the new role to assign to the target user
 */
data class ChangeUserRoleCommandDTO(
    @field:NotNull
    val role: UserRole?,
)
