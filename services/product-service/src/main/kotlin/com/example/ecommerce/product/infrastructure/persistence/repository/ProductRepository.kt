package com.example.ecommerce.product.infrastructure.persistence.repository

import com.example.ecommerce.product.application.port.out.ProductRepositoryPort
import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.persistence.mapper.ProductPersistenceMapper
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class ProductRepository(
    private val jpaProductRepository: JpaProductRepository,
    private val productPersistenceMapper: ProductPersistenceMapper,
) : ProductRepositoryPort {

    override fun findById(id: ProductId): Optional<Product> =
        jpaProductRepository.findById(id.value!!).map { productPersistenceMapper.toDomain(it) }

    override fun findAll(): List<Product> =
        jpaProductRepository.findAll().map { productPersistenceMapper.toDomain(it) }

    override fun save(product: Product): Product =
        productPersistenceMapper.toDomain(jpaProductRepository.save(productPersistenceMapper.toEntity(product)))

    override fun deleteById(id: ProductId) {
        jpaProductRepository.deleteById(id.value!!)
    }
}
