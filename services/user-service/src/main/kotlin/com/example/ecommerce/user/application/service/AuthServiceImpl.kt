package com.example.ecommerce.user.application.service

import com.example.ecommerce.user.application.dto.command.LoginCommandDTO
import com.example.ecommerce.user.application.dto.result.AuthResultDTO
import com.example.ecommerce.user.application.exception.InvalidCredentialsException
import com.example.ecommerce.user.application.port.`in`.AuthServicePort
import com.example.ecommerce.user.domain.valueobject.UserId
import com.example.ecommerce.user.infrastructure.security.JwtService
import com.example.ecommerce.user.infrastructure.security.SecurityUserDetails
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Service

/**
 * Authenticates credentials via Spring Security's [AuthenticationManager] and issues an
 * RS256 JWT (via [JwtService]) on success. No refresh tokens are issued; an expired token
 * forces a new login.
 */
@Service
class AuthServiceImpl(
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
) : AuthServicePort {

    /**
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     */
    override fun login(cmd: LoginCommandDTO): AuthResultDTO {
        val authentication: Authentication
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(cmd.email, cmd.password),
            )
        } catch (ex: AuthenticationException) {
            throw InvalidCredentialsException("Invalid email or password")
        }

        val principal = authentication.principal as SecurityUserDetails
        val token = jwtService.generateToken(UserId.of(principal.userId), principal.email, principal.role!!)
        return AuthResultDTO.of(token)
    }
}
