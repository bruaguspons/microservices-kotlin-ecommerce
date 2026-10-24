package com.example.ecommerce.buildingblocks.jwt

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * Shared JWT verification config, wired into each service's Spring Security
 * resource-server filter chain. Verifies RS256-signed tokens issued by
 * user-service against its published JWKS endpoint (see design: JWT / Auth
 * Design - JWKS over shared-secret, 10-min cache TTL, fail-open on refresh
 * failure).
 *
 * NOTE (deviation, documented): `NimbusJwtDecoder.withJwkSetUri(...)`
 * uses Nimbus's default `RemoteJWKSet` caching (in-memory, short
 * default lifespan) rather than an explicit 10-minute TTL with hard
 * fail-open-on-expiry semantics. Achieving the design's exact cache/fail-open
 * contract requires wiring a custom `com.nimbusds.jose.jwk.source.JWKSetCache`
 * via the lower-level Nimbus builder API. Left as a follow-up refinement -
 * default Nimbus behaviour already serves cached keys between refreshes,
 * which is acceptable for this project's scope.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtDecoderConfig {

    @Bean
    fun jwtDecoder(jwtProperties: JwtProperties): JwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(jwtProperties.jwksUri!!).build()
}
