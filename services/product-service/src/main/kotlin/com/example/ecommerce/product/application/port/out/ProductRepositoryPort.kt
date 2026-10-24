package com.example.ecommerce.product.application.port.out

import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import java.util.Optional

interface ProductRepositoryPort {
    fun findById(id: ProductId): Optional<Product>

    fun findAll(): List<Product>

    fun save(product: Product): Product

    fun deleteById(id: ProductId)
}
