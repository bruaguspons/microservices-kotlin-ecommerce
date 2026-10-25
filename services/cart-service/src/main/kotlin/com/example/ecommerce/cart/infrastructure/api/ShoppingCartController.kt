package com.example.ecommerce.cart.infrastructure.api

import com.example.ecommerce.cart.application.dto.command.CreateShoppingCartDTO
import com.example.ecommerce.cart.application.dto.result.ShoppingCartResultDTO
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shopping-carts")
@Tag(name = "Shopping Carts", description = "Shopping cart management. All endpoints require authentication; access is restricted to the cart owner or an ADMIN.")
class ShoppingCartController(
    private val shoppingCartService: ShoppingCartServicePort,
) {

    @Operation(summary = "Get shopping cart by id", description = "Returns a single shopping cart (with its line items) by its id. Requires ownership of the cart or the ADMIN role.")
    @GetMapping("/{id}")
    fun findById(@PathVariable id: ShoppingCartId): ResponseEntity<ShoppingCartResultDTO> =
        ResponseEntity.ok(shoppingCartService.findById(id.value))

    @Operation(summary = "List all shopping carts", description = "Returns the full, unpaginated list of every shopping cart in the system. Admin only.")
    @GetMapping("")
    fun findAll(): ResponseEntity<List<ShoppingCartResultDTO>> =
        ResponseEntity.ok(shoppingCartService.findAll())

    @Operation(summary = "Create a shopping cart", description = "Creates a new shopping cart for a user. Validates the user exists via user-service. Requires the caller to be creating their own cart, or the ADMIN role.")
    @PostMapping("")
    fun create(@Valid @RequestBody cmd: CreateShoppingCartDTO): ResponseEntity<ShoppingCartResultDTO> =
        ResponseEntity.ok(shoppingCartService.create(cmd))

    @Operation(summary = "Pay a shopping cart", description = "Marks the cart as paid and publishes a cart.paid event. Requires ownership of the cart or the ADMIN role.")
    @PostMapping("/{id}/pay")
    fun pay(@PathVariable id: ShoppingCartId): ResponseEntity<ShoppingCartResultDTO> =
        ResponseEntity.ok(shoppingCartService.pay(id.value))

    @Operation(summary = "Delete a shopping cart", description = "Deletes a shopping cart by its id. Requires ownership of the cart or the ADMIN role.")
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: ShoppingCartId): ResponseEntity<Void> {
        shoppingCartService.deleteById(id.value)
        return ResponseEntity.noContent().build()
    }
}
