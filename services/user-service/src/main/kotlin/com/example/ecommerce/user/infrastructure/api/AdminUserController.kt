package com.example.ecommerce.user.infrastructure.api

import com.example.ecommerce.user.application.dto.command.AdminCreateUserCommandDTO
import com.example.ecommerce.user.application.dto.command.ChangeUserRoleCommandDTO
import com.example.ecommerce.user.application.dto.result.UserResultDTO
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * ADMIN-only user-management endpoints, kept separate from [UserController] because
 * every method here is ADMIN-only by construction, whereas [UserController] mixes
 * public/self/admin actions.
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin Users", description = "ADMIN-only user management: creating users with an explicit role and changing an existing user's role.")
class AdminUserController(
    private val userService: UserServicePort,
) {

    /**
     * Creates a user with an explicit, arbitrary role. ADMIN-only.
     *
     * @param cmd the creation payload, including the role to assign
     * @return 201 with the created user
     */
    @Operation(summary = "Create a user with an explicit role", description = "Admin only. Creates a user with an arbitrary role assigned by the caller, unlike public self-registration which always assigns USER.")
    @PostMapping
    fun create(@Valid @RequestBody cmd: AdminCreateUserCommandDTO): ResponseEntity<UserResultDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(userService.createByAdmin(cmd))

    /**
     * Changes an existing user's role. ADMIN-only.
     *
     * @param id the target user's id
     * @param cmd the new role
     * @return 200 with the updated user
     */
    @Operation(summary = "Change a user's role", description = "Admin only. Changes the role of an existing user.")
    @PatchMapping("/{id}/role")
    fun changeRole(@PathVariable id: UUID, @Valid @RequestBody cmd: ChangeUserRoleCommandDTO): ResponseEntity<UserResultDTO> =
        ResponseEntity.ok(userService.changeRole(id, cmd))
}
