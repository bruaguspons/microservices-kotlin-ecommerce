package com.example.ecommerce.product.infrastructure.persistence.mapper

import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.persistence.entity.ProductEntity
import org.springframework.stereotype.Component

@Component
class ProductPersistenceMapper {

    fun toEntity(product: Product): ProductEntity = ProductEntity(
        id = product.id!!.value,
        createdAt = product.createdAt,
        createdBy = product.createdBy,
        lastModified = product.lastModified,
        lastModifiedBy = product.lastModifiedBy,
        version = product.version,
        isDeleted = product.isDeleted,
        name = product.name,
        desc = product.desc,
        price = product.price,
        imagePath = product.imagePath,
        category = product.category,
    )

    fun toDomain(entity: ProductEntity): Product = Product.reconstitute(
        ProductId.of(entity.id),
        entity.createdAt,
        entity.createdBy,
        entity.lastModified,
        entity.lastModifiedBy,
        entity.version,
        entity.isDeleted,
        entity.name,
        entity.desc,
        entity.price,
        entity.imagePath,
        entity.category,
    )
}
