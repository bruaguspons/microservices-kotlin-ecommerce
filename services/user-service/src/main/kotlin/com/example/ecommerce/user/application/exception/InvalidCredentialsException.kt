package com.example.ecommerce.user.application.exception

/**
 * Thrown when a login attempt supplies an unknown email or a wrong password.
 */
class InvalidCredentialsException(message: String?) : RuntimeException(message)
