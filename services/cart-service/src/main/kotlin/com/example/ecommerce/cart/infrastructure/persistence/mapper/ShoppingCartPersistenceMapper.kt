package com.example.ecommerce.cart.infrastructure.persistence.mapper

import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.persistence.entity.ShoppingCartEntity
import org.springframework.stereotype.Component

@Component
class ShoppingCartPersistenceMapper(
    private val itemMapper: ProductShoppingCartPersistenceMapper,
) {

    fun toEntity(sc: ShoppingCart): ShoppingCartEntity = ShoppingCartEntity(
        id = sc.id!!.value,
        createdAt = sc.createdAt,
        createdBy = sc.createdBy,
        lastModified = sc.lastModified,
        lastModifiedBy = sc.lastModifiedBy,
        version = sc.version,
        isDeleted = sc.isDeleted,
        total = sc.total,
        payed = sc.payed,
        userId = sc.userId!!.value,
        // Stream.toList() on purpose: produces an unmodifiable list.
        items = sc.items!!.stream()
            .map { itemMapper.toEntity(it) }
            .toList(),
    )

    fun toDomain(sc: ShoppingCartEntity): ShoppingCart = ShoppingCart.reconstitute(
        ShoppingCartId.of(sc.id),
        sc.createdAt,
        sc.createdBy,
        sc.lastModified,
        sc.lastModifiedBy,
        sc.version,
        sc.isDeleted,
        sc.total,
        sc.payed,
        UserId.of(sc.userId),
        sc.items!!
            .map { itemMapper.toDomain(it) }
            .toMutableList(),
    )
}
