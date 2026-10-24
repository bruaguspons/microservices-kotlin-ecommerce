package com.example.ecommerce.product.infrastructure.api

import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.dto.command.UpdateProductCommandDTO
import com.example.ecommerce.product.application.dto.result.ProductResultDTO
import com.example.ecommerce.product.application.port.`in`.ProductServicePort
import com.example.ecommerce.product.domain.valueobject.ProductId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product catalog management. Reads are public; create, update, and delete require the ADMIN role.")
class ProductController(
    private val productService: ProductServicePort,
) {

    @Operation(summary = "Get product by id", description = "Returns a single product by its id. Public — no authentication required.")
    @GetMapping("/{id}")
    fun findById(@PathVariable id: ProductId): ResponseEntity<ProductResultDTO> =
        ResponseEntity.ok(productService.findById(id))

    @Operation(summary = "List all products", description = "Returns the full, unpaginated list of products. Public — no authentication required.")
    @GetMapping("")
    fun findAll(): ResponseEntity<List<ProductResultDTO>> =
        ResponseEntity.ok(productService.findAll())

    @Operation(summary = "Create a product", description = "Creates a new product. Admin only.")
    @PostMapping("")
    fun create(@Valid @RequestBody entity: CreateProductCommandDTO): ResponseEntity<ProductResultDTO> =
        ResponseEntity.ok(productService.create(entity))

    @Operation(summary = "Update a product", description = "Updates an existing product's fields. Admin only.")
    @PutMapping("/{id}")
    fun update(@PathVariable id: ProductId, @Valid @RequestBody entity: UpdateProductCommandDTO): ResponseEntity<ProductResultDTO> =
        ResponseEntity.ok(productService.update(id, entity))

    @Operation(summary = "Delete a product", description = "Deletes a product by its id. Admin only.")
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: ProductId): ResponseEntity<Void> {
        productService.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
