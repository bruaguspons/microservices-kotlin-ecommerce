package com.example.ecommerce.buildingblocks.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class JwtAuthConverterTest {

    private val converter = JwtAuthConverter()

    private fun jwt(): Jwt.Builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-sub")
            .issuedAt(Instant.parse("2024-01-01T00:00:00Z"))
            .expiresAt(Instant.parse("2024-01-02T00:00:00Z"))

    @Test
    fun adminRoleMapsToRolePrefixedAuthority() {
        val token = converter.convert(jwt().claim("role", "ADMIN").build())

        assertThat(token).isInstanceOf(JwtAuthenticationToken::class.java)
        assertThat(token.name).isEqualTo("user-sub")
        assertThat(token.authorities.map { it.authority })
            .containsExactly("ROLE_ADMIN")
    }

    @Test
    fun blankRoleGivesNoAuthorities() {
        val token = converter.convert(jwt().claim("role", "  ").build())

        assertThat(token.authorities).isEmpty()
        assertThat(token.name).isEqualTo("user-sub")
    }

    @Test
    fun missingRoleGivesNoAuthorities() {
        val token = converter.convert(jwt().claim("email", "a@b.com").build())

        assertThat(token.authorities).isEmpty()
    }

    @Test
    fun roleIsNotUppercasedOrNormalized() {
        val token = converter.convert(jwt().claim("role", "user").build())

        assertThat(token.authorities.map { it.authority })
            .containsExactly("ROLE_user")
    }
}
