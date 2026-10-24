package com.example.ecommerce.cart.infrastructure.persistence.mapper

import com.example.ecommerce.cart.domain.model.ProductShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ProductShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.infrastructure.persistence.entity.ProductShoppingCartEntity
import com.example.ecommerce.cart.infrastructure.persistence.entity.ShoppingCartEntity
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class ProductShoppingCartPersistenceMapper(
    private val entityManager: EntityManager,
) {

    fun toEntity(item: ProductShoppingCart): ProductShoppingCartEntity {
        // Managed proxy (not a bare id-only builder stub) so Hibernate treats the
        // association as an existing, already-persisted reference.
        val scRef = entityManager.getReference(ShoppingCartEntity::class.java, item.shoppingCartId!!.value)

        return ProductShoppingCartEntity(
            id = item.id!!.value,
            createdAt = item.createdAt,
            createdBy = item.createdBy,
            lastModified = item.lastModified,
            lastModifiedBy = item.lastModifiedBy,
            version = item.version,
            isDeleted = item.isDeleted,
            shoppingCart = scRef,
            productId = item.productId!!.value,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            priceVersion = item.priceVersion,
        )
    }

    fun toDomain(entity: ProductShoppingCartEntity): ProductShoppingCart = ProductShoppingCart.reconstitute(
        ProductShoppingCartId.of(entity.id),
        entity.createdAt,
        entity.createdBy,
        entity.lastModified,
        entity.lastModifiedBy,
        entity.version,
        entity.isDeleted,
        ProductId.of(entity.productId),
        ShoppingCartId.of(entity.shoppingCart!!.id),
        entity.quantity,
        entity.unitPrice,
        entity.priceVersion,
    )
}
