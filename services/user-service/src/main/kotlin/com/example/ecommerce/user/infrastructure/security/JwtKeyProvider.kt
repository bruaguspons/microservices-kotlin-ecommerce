package com.example.ecommerce.user.infrastructure.security

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Holds the RSA keypair user-service uses to sign issued JWTs ([JwtService])
 * and to publish its JWKS document (`JwkSetController`).
 *
 * Key sourcing (see design: "Private key lives ONLY in user-service's
 * config/secret store" + this work-unit's documented local-dev fallback):
 *
 *  - If `jwt.private-key-path` is set (Docker Compose mounts the
 *    `jwt_private_key` secret at that path in production), the
 *    PKCS#8 PEM-encoded private key is loaded from that file, and the
 *    matching RSA public key is derived from its CRT parameters
 *    (modulus + public exponent) - Compose only distributes the private
 *    key material, never a separate public key file.
 *  - Otherwise (local `spring-boot:run` without Compose), an
 *    ephemeral RSA-2048 keypair is generated in memory at startup. This
 *    is a deliberate, documented deviation for local-dev convenience:
 *    tokens signed with it do not survive a service restart and this
 *    path MUST NOT be used in production - set
 *    `JWT_PRIVATE_KEY_PATH` there instead.
 */
@Component
class JwtKeyProvider(properties: RsaKeyProperties) {

    final val privateKey: PrivateKey
    final val publicKey: RSAPublicKey
    final val keyId: String?

    /**
     * Enables [RsaKeyProperties] binding. Kept as a nested
     * `@Configuration` class so [JwtKeyProvider] itself can stay
     * a plain `@Component` constructor-injected with the bound
     * properties bean.
     */
    @Configuration
    @EnableConfigurationProperties(RsaKeyProperties::class)
    class PropertiesEnabler

    init {
        this.keyId = properties.keyId
        if (StringUtils.hasText(properties.privateKeyPath)) {
            val loaded = loadPrivateKey(properties.privateKeyPath!!)
            this.privateKey = loaded
            this.publicKey = derivePublicKey(loaded)
            log.info("Loaded RSA signing key from {}", properties.privateKeyPath)
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val generated = generator.generateKeyPair()
            this.privateKey = generated.private
            this.publicKey = generated.public as RSAPublicKey
            log.warn(
                "No jwt.private-key-path configured — generated an EPHEMERAL RSA-2048 keypair " +
                    "for local development. Issued tokens will stop verifying on the next restart. " +
                    "Set JWT_PRIVATE_KEY_PATH (Docker Compose secret) in any non-local environment.",
            )
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(JwtKeyProvider::class.java)

        private fun loadPrivateKey(path: String): PrivateKey {
            val pem = Files.readString(Path.of(path))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), "")
            val decoded = Base64.getDecoder().decode(pem)
            val spec = PKCS8EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        }

        private fun derivePublicKey(privateKey: PrivateKey): RSAPublicKey {
            val crtKey = privateKey as RSAPrivateCrtKey
            val publicKeySpec = RSAPublicKeySpec(crtKey.modulus, crtKey.publicExponent)
            return KeyFactory.getInstance("RSA").generatePublic(publicKeySpec) as RSAPublicKey
        }
    }
}
