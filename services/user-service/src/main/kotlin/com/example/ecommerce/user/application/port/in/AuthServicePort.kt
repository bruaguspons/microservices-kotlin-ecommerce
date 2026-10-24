package com.example.ecommerce.user.application.port.`in`

import com.example.ecommerce.user.application.dto.command.LoginCommandDTO
import com.example.ecommerce.user.application.dto.result.AuthResultDTO

/**
 * Inbound port for the authentication capability.
 */
interface AuthServicePort {

    /**
     * Authenticates a user with email/password and issues a JWT.
     *
     * @param cmd the login credentials
     * @return the issued JWT wrapped in an [AuthResultDTO]
     */
    fun login(cmd: LoginCommandDTO): AuthResultDTO
}
