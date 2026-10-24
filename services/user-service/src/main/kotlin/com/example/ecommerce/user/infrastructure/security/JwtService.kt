package com.example.ecommerce.user.infrastructure.security

import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.valueobject.UserId
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date

/**
 * Issues RS256-signed JWTs using the RSA private key held by
 * [JwtKeyProvider]. This class ONLY issues tokens - verification of
 * incoming requests is handled independently by buildingblocks'
 * `JwtDecoder`/`JwtAuthConverter` (JWKS-based OAuth2
 * resource-server config), the same mechanism product-service/cart-service
 * use.
 *
 * Claim schema per design: `{sub, email, role, iat, exp}`. No
 * refresh-token mechanism: an expired token simply requires the client to
 * authenticate again via `POST /api/auth/login`.
 */
@Component
class JwtService(
    private val keyProvider: JwtKeyProvider,
) {

    @field:Value("\${app.security.jwt.expiration-ms}")
    private var expirationMillis: Long = 0L

    /**
     * Generates a signed JWT for the given user identity, email, and role.
     *
     * @param userId the acting user's identifier
     * @param email the acting user's email
     * @param role the acting user's role
     * @return a compact, RS256-signed JWT string
     */
    fun generateToken(userId: UserId, email: String?, role: UserRole): String {
        val now = Instant.now()
        return Jwts.builder()
            .header().keyId(keyProvider.keyId).and()
            .subject(userId.value!!.toString())
            .claim("email", email)
            .claim("role", role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMillis)))
            .signWith(keyProvider.privateKey, Jwts.SIG.RS256)
            .compact()
    }
}
