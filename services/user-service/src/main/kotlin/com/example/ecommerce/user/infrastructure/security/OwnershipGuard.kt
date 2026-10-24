package com.example.ecommerce.user.infrastructure.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Ownership-check mechanism used by `@PreAuthorize` SpEL expressions
 * on `UserServiceImpl` (e.g. `@ownership.isSelf(#id)`).
 *
 * Registered as the named bean `ownership`. The current principal's
 * identity comes from the authenticated JWT's `sub` claim
 * (buildingblocks' `JwtAuthConverter` sets it as
 * [Authentication.getName]), not a `SecurityUserDetails`
 * principal - that class is only built during login (see
 * [SecurityUserDetailsService]).
 */
@Component("ownership")
class OwnershipGuard {

    /**
     * Checks whether the currently authenticated principal's user id matches the given id.
     *
     * @param userId the user id to compare against the current principal
     * @return `true` if the current principal is that user, `false` otherwise
     */
    fun isSelf(userId: UUID?): Boolean {
        val id = currentUserId() ?: return false
        return id == userId
    }

    private fun currentUserId(): UUID? {
        val authentication: Authentication? = SecurityContextHolder.getContext().authentication
        if (authentication == null || authentication.name == null) {
            return null
        }
        return try {
            UUID.fromString(authentication.name)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }
}
