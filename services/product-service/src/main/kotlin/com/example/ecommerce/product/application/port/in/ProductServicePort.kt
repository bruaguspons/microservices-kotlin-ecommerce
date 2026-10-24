package com.example.ecommerce.product.application.port.`in`

import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.dto.command.UpdateProductCommandDTO
import com.example.ecommerce.product.application.dto.result.ProductResultDTO
import com.example.ecommerce.product.domain.valueobject.ProductId

interface ProductServicePort {
    fun findById(id: ProductId): ProductResultDTO

    fun findAll(): List<ProductResultDTO>

    fun create(product: CreateProductCommandDTO): ProductResultDTO

    fun update(id: ProductId, product: UpdateProductCommandDTO): ProductResultDTO

    fun deleteById(id: ProductId)
}
