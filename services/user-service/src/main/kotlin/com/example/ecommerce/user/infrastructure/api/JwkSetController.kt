package com.example.ecommerce.user.infrastructure.api

import com.example.ecommerce.user.infrastructure.security.JwtKeyProvider
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Publishes user-service's RSA public key as a standard JWKS (JSON Web Key
 * Set) document - the trust-distribution mechanism product-service and
 * cart-service pull from via buildingblocks' `JwtDecoder` (see design:
 * "Why JWKS endpoint over shared-config public key file"). Public,
 * unauthenticated route, routed through the gateway.
 */
@RestController
@Tag(name = "JWKS", description = "Publishes the RSA public key used to verify JWTs issued by this service.")
class JwkSetController(
    private val keyProvider: JwtKeyProvider,
) {

    @Operation(summary = "Get the JWK Set", description = "Public. Returns the RSA public key(s) used to verify tokens issued by POST /api/auth/login, as a standard JWK Set document.")
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> {
        val jwk = RSAKey.Builder(keyProvider.publicKey)
            .keyID(keyProvider.keyId)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build()
        return JWKSet(jwk).toJSONObject()
    }
}
