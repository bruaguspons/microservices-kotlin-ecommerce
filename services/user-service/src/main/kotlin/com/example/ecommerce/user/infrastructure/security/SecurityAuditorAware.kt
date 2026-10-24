package com.example.ecommerce.user.infrastructure.security

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.UUID

/**
 * Resolves the current auditor for Spring Data JPA's `@CreatedBy`/`@LastModifiedBy`
 * from the authenticated principal's JWT `sub` claim (buildingblocks'
 * `JwtAuthConverter` sets it as `Authentication.getName()`).
 *
 * Returns empty (never throws) when there is no authenticated principal in
 * context, per Spring Data JPA auditing conventions.
 */
@Component
class SecurityAuditorAware : AuditorAware<UUID> {

    override fun getCurrentAuditor(): Optional<UUID> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.name == null) {
            return Optional.empty()
        }
        return try {
            Optional.of(UUID.fromString(authentication.name))
        } catch (ex: IllegalArgumentException) {
            Optional.empty()
        }
    }
}
