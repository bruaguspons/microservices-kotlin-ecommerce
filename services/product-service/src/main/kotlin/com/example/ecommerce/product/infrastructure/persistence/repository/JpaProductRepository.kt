package com.example.ecommerce.product.infrastructure.persistence.repository

import com.example.ecommerce.product.infrastructure.persistence.entity.ProductEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaProductRepository : JpaRepository<ProductEntity, UUID>
