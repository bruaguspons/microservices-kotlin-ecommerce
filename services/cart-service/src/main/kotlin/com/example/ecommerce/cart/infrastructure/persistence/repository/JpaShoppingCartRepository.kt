package com.example.ecommerce.cart.infrastructure.persistence.repository

import com.example.ecommerce.cart.infrastructure.persistence.entity.ShoppingCartEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JpaShoppingCartRepository : JpaRepository<ShoppingCartEntity, UUID> {

    /**
     * Filters `payed = false` at the query level (see design: Product
     * Price-Change Propagation) so paid carts are never loaded, not just
     * skipped in memory - enforces the paid-cart-immutability invariant at
     * the persistence boundary.
     */
    @Query("SELECT DISTINCT c FROM ShoppingCartEntity c JOIN c.items i WHERE i.productId = :productId AND c.payed = false")
    fun findOpenCartsContainingProduct(@Param("productId") productId: UUID): List<ShoppingCartEntity>
}
