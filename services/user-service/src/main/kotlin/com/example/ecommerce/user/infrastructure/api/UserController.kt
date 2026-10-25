package com.example.ecommerce.user.infrastructure.api

import com.example.ecommerce.user.application.dto.command.RegisterUserCommandDTO
import com.example.ecommerce.user.application.dto.command.UpdateUserCommandDTO
import com.example.ecommerce.user.application.dto.result.UserResultDTO
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import com.example.ecommerce.user.domain.valueobject.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account management. Registration is public; other operations are restricted to the target user (self) or an ADMIN.")
class UserController(
    private val userService: UserServicePort,
) {

    @Operation(summary = "Get user by id", description = "Returns a single user by their id. Requires the caller to be that user (self) or the ADMIN role.")
    @GetMapping("/{id}")
    fun findById(@PathVariable id: UserId): ResponseEntity<UserResultDTO> =
        ResponseEntity.ok(userService.findById(id.value!!))

    @Operation(summary = "Get user by email", description = "Returns a single user by their email address. Admin only.")
    @GetMapping("/email/{email}")
    fun findByEmail(@PathVariable email: String): ResponseEntity<UserResultDTO> =
        ResponseEntity.ok(userService.findByEmail(email))

    @Operation(summary = "List all users", description = "Returns the full, unpaginated list of every user in the system. Admin only.")
    @GetMapping("")
    fun findAll(): ResponseEntity<List<UserResultDTO>> =
        ResponseEntity.ok(userService.findAll())

    @Operation(summary = "Register a new user", description = "Public self-registration endpoint. The created user is always assigned the USER role, regardless of request input.")
    @PostMapping("")
    fun register(@Valid @RequestBody entity: RegisterUserCommandDTO): ResponseEntity<UserResultDTO> =
        ResponseEntity.ok(userService.register(entity))

    @Operation(summary = "Update a user", description = "Updates an existing user's fields. Requires the caller to be that user (self) or the ADMIN role.")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UserId, @Valid @RequestBody entity: UpdateUserCommandDTO): ResponseEntity<UserResultDTO> =
        ResponseEntity.ok(userService.update(id.value!!, entity))

    @Operation(summary = "Delete a user", description = "Deletes a user by their id. Requires the caller to be that user (self) or the ADMIN role.")
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: UserId): ResponseEntity<Void> {
        userService.deleteById(id.value!!)
        return ResponseEntity.noContent().build()
    }
}
