package com.example.ecommerce.user.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds the RSA signing-key configuration for JWT issuance (see design:
 * "JWT / Auth Design - RESOLVED").
 *
 * `privateKeyPath` is expected to point at a PKCS#8 PEM-encoded RSA
 * private key file. In Docker Compose, this is the path Compose mounts the
 * `jwt_private_key` secret at (`/run/secrets/jwt_private_key`).
 * For local `spring-boot:run` dev without Compose, this property is
 * left unset and [JwtKeyProvider] generates an ephemeral in-memory
 * RSA-2048 keypair instead - tokens signed with it only remain valid for the
 * lifetime of that JVM process, which is acceptable for local development
 * but MUST NOT be relied on across service restarts or in production.
 */
@ConfigurationProperties(prefix = "jwt")
class RsaKeyProperties {
    var privateKeyPath: String? = null
    var keyId: String? = "user-service-key-1"
}
