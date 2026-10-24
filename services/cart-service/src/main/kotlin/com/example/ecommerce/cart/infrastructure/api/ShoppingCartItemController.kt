package com.example.ecommerce.cart.infrastructure.api

import com.example.ecommerce.cart.application.dto.command.AddItemCommandDTO
import com.example.ecommerce.cart.application.dto.command.UpdateItemQuantityCommandDTO
import com.example.ecommerce.cart.application.dto.result.ProductShoppingCartResultDTO
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Line-item operations as RESTful sub-resources under the owning cart (per
 * judgment-day-approved design: no top-level
 * `/api/products-shopping-cart` route family exists anymore). PATCH only
 * for quantity updates - no PUT, partial-update semantics only.
 */
@RestController
@RequestMapping("/api/shopping-carts/{cartId}/items")
@Tag(name = "Shopping Cart Items", description = "Line-item management within a shopping cart. All endpoints require authentication; access is restricted to the cart owner or an ADMIN.")
class ShoppingCartItemController(
    private val shoppingCartService: ShoppingCartServicePort,
) {

    @Operation(summary = "Add a line item to a cart", description = "Adds a product to the cart, fetching the current price from product-service and snapshotting it. Requires ownership of the cart or the ADMIN role.")
    @PostMapping("")
    fun addItem(@PathVariable cartId: UUID, @Valid @RequestBody cmd: AddItemCommandDTO): ResponseEntity<ProductShoppingCartResultDTO> =
        ResponseEntity.ok(shoppingCartService.addItem(cartId, cmd))

    @Operation(summary = "Update a cart line item's quantity", description = "Partial update: changes only the quantity of an existing line item. Requires ownership of the cart or the ADMIN role.")
    @PatchMapping("/{itemId}")
    fun updateItemQuantity(
        @PathVariable cartId: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody cmd: UpdateItemQuantityCommandDTO,
    ): ResponseEntity<ProductShoppingCartResultDTO> =
        ResponseEntity.ok(shoppingCartService.updateItemQuantity(cartId, itemId, cmd))

    @Operation(summary = "Remove a cart line item", description = "Removes a line item from its shopping cart. Requires ownership of the cart or the ADMIN role.")
    @DeleteMapping("/{itemId}")
    fun removeItem(@PathVariable cartId: UUID, @PathVariable itemId: UUID): ResponseEntity<Void> {
        shoppingCartService.removeItem(cartId, itemId)
        return ResponseEntity.noContent().build()
    }
}
