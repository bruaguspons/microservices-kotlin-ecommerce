package com.example.ecommerce.buildingblocks.jwt

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Maps decoded JWT claims into a Spring Security [AbstractAuthenticationToken],
 * per the design's JWT payload schema: `sub` (user id) becomes the
 * authentication principal name, `role` becomes a single
 * [GrantedAuthority]. Each service's SecurityConfig wires this in via
 * `oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(this)))`.
 *
 * Consuming services are responsible for converting `jwt.getSubject()`
 * (a UUID string) into their own `UserId` value object - buildingblocks
 * does not own that type.
 */
@Component
class JwtAuthConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken =
        JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.subject!!)

    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val role = jwt.getClaimAsString(ROLE_CLAIM)
        if (role == null || role.isBlank()) {
            return listOf()
        }
        return listOf(SimpleGrantedAuthority(ROLE_PREFIX + role))
    }

    companion object {
        private const val ROLE_CLAIM = "role"
        private const val ROLE_PREFIX = "ROLE_"
    }
}
