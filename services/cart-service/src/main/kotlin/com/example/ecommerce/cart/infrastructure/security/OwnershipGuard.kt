package com.example.ecommerce.cart.infrastructure.security

import com.example.ecommerce.cart.application.port.out.ShoppingCartRepositoryPort
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * cart-service-scoped ownership-check mechanism for `@PreAuthorize`
 * SpEL expressions. Registered as the named bean `ownership`.
 *
 * Reads `Authentication.getName()` (set by buildingblocks'
 * `JwtAuthConverter` to the JWT's `sub` claim), NOT a cast to a
 * custom principal type - this service is a pure OAuth2-resource-server JWT
 * verifier, it never issues tokens (see WU4 apply notes: the gotcha carries
 * forward from user-service's OwnershipGuard rewrite).
 */
@Component("ownership")
class OwnershipGuard(
    private val shoppingCartRepository: ShoppingCartRepositoryPort,
) {

    fun isSelf(userId: UUID?): Boolean {
        val id = currentUserId() ?: return false
        return id == userId
    }

    /**
     * Runs read-only-transactional: `@PreAuthorize` SpEL evaluation happens
     * BEFORE the target method's own `@Transactional` proxy opens a transaction,
     * and resolving the cart eagerly reads the lazy `items` collection while
     * mapping to the domain model (this app runs with `open-in-view: false`).
     */
    @Transactional(readOnly = true)
    fun isCartOwner(cartId: UUID?): Boolean =
        shoppingCartRepository.findById(ShoppingCartId.of(cartId))
            .map { cart -> isSelf(cart.userId!!.value) }
            .orElse(false)

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
