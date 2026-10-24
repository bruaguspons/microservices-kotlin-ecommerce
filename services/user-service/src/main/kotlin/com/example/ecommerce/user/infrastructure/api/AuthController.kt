package com.example.ecommerce.user.infrastructure.api

import com.example.ecommerce.user.application.dto.command.LoginCommandDTO
import com.example.ecommerce.user.application.dto.result.AuthResultDTO
import com.example.ecommerce.user.application.port.`in`.AuthServicePort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public authentication endpoint.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public authentication endpoint for issuing JWTs.")
class AuthController(
    private val authService: AuthServicePort,
) {

    /**
     * Authenticates a user and issues a JWT.
     *
     * @param cmd the login credentials
     * @return 200 with the issued token on success
     */
    @Operation(summary = "Log in", description = "Authenticates a user's credentials and issues an RS256 JWT. Public — no authentication required.")
    @PostMapping("/login")
    fun login(@Valid @RequestBody cmd: LoginCommandDTO): ResponseEntity<AuthResultDTO> =
        ResponseEntity.ok(authService.login(cmd))
}
