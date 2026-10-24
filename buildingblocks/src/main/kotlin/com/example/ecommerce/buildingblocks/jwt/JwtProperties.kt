package com.example.ecommerce.buildingblocks.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds `jwt.jwks-uri`, set per-service to the JWKS endpoint published
 * by user-service (see design: JWT / Auth Design). This is a public URL, not
 * a secret, so no coordinated rotation/redeploy is needed across services.
 */
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {

    var jwksUri: String? = null
}
