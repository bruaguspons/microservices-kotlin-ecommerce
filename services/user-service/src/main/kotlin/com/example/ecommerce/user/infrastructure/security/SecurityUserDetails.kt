package com.example.ecommerce.user.infrastructure.security

import com.example.ecommerce.user.domain.enums.UserRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security [UserDetails] adapter used ONLY during
 * `POST /api/auth/login` (loaded by [SecurityUserDetailsService]
 * to authenticate email/password). Requests to already-protected endpoints
 * do not rebuild this from a manually-parsed token - they go through
 * buildingblocks' OAuth2 resource-server `JwtDecoder` +
 * `JwtAuthConverter` instead, whose principal is a `Jwt`, not
 * this class.
 */
class SecurityUserDetails(
    val userId: UUID?,
    val email: String?,
    val passwordHash: String?,
    val role: UserRole?,
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_" + role!!.name))

    override fun getPassword(): String? = passwordHash

    override fun getUsername(): String = email!!
}
