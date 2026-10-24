package com.example.ecommerce.cart.infrastructure.persistence.repository

import com.example.ecommerce.cart.application.port.out.ShoppingCartRepositoryPort
import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.infrastructure.persistence.mapper.ShoppingCartPersistenceMapper
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class ShoppingCartRepository(
    private val shoppingCartMapper: ShoppingCartPersistenceMapper,
    private val repository: JpaShoppingCartRepository,
) : ShoppingCartRepositoryPort {

    override fun findById(id: ShoppingCartId): Optional<ShoppingCart> =
        repository.findById(id.value!!).map { shoppingCartMapper.toDomain(it) }

    override fun findAll(): List<ShoppingCart> =
        repository.findAll().map { shoppingCartMapper.toDomain(it) }

    override fun findOpenCartsContainingProduct(productId: ProductId): List<ShoppingCart> =
        repository.findOpenCartsContainingProduct(productId.value!!).map { shoppingCartMapper.toDomain(it) }

    override fun save(shoppingCart: ShoppingCart): ShoppingCart =
        shoppingCartMapper.toDomain(repository.save(shoppingCartMapper.toEntity(shoppingCart)))

    override fun deleteById(id: ShoppingCartId) {
        repository.deleteById(id.value!!)
    }
}
