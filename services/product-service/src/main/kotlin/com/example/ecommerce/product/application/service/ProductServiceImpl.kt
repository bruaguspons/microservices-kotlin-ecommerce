package com.example.ecommerce.product.application.service

import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.dto.command.UpdateProductCommandDTO
import com.example.ecommerce.product.application.dto.result.ProductResultDTO
import com.example.ecommerce.product.application.exception.ProductNotFoundException
import com.example.ecommerce.product.application.port.`in`.ProductServicePort
import com.example.ecommerce.product.application.port.out.ProductRepositoryPort
import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.event.ProductPriceChangedEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepositoryPort,
    private val productPriceChangedEventPublisher: ProductPriceChangedEventPublisher,
) : ProductServicePort {

    override fun findById(id: ProductId): ProductResultDTO {
        val product = productRepository.findById(id)
            .orElseThrow { ProductNotFoundException("Product not found with id: $id") }

        return ProductResultDTO.from(product)
    }

    override fun findAll(): List<ProductResultDTO> =
        productRepository.findAll().map { ProductResultDTO.from(it) }

    @PreAuthorize("hasRole('ADMIN')")
    override fun create(product: CreateProductCommandDTO): ProductResultDTO {
        val created = Product.create(
            product.name,
            product.desc,
            product.price,
            product.imagePath,
            product.category,
        )

        return ProductResultDTO.from(productRepository.save(created))
    }

    /**
     * Updates a product. When the price changes, publishes a
     * `product.price-changed` event via the transactional outbox in the
     * SAME transaction as the update (see spec: Price update triggers event).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    override fun update(id: ProductId, product: UpdateProductCommandDTO): ProductResultDTO {
        val existing = productRepository.findById(id)
            .orElseThrow { ProductNotFoundException("Product not found with id: $id") }

        val previousPrice = existing.price

        val updated = Product.reconstitute(
            existing.id,
            existing.createdAt,
            existing.createdBy,
            existing.lastModified,
            existing.lastModifiedBy,
            existing.version,
            existing.isDeleted,
            product.name,
            product.desc,
            product.price,
            product.imagePath,
            product.category,
        )

        val saved = productRepository.save(updated)

        if (previousPrice!!.compareTo(saved.price!!) != 0) {
            productPriceChangedEventPublisher.publish(saved)
        }

        return ProductResultDTO.from(saved)
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun deleteById(id: ProductId) {
        productRepository.deleteById(id)
    }
}
