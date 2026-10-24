package com.example.ecommerce.user.application.dto.result

/**
 * Successful login response, carrying the issued JWT.
 *
 * @param token the signed RS256 JWT
 */
data class AuthResultDTO(
    val token: String?,
) {

    companion object {
        /**
         * Wraps a raw token string into an [AuthResultDTO].
         *
         * @param token the signed JWT
         * @return a new [AuthResultDTO]
         */
        @JvmStatic
        fun of(token: String?): AuthResultDTO = AuthResultDTO(token)
    }
}
