package com.example.ecommerce.cart.application.dto.result

import com.example.ecommerce.cart.domain.model.ShoppingCart
import java.math.BigDecimal
import java.util.UUID

data class ShoppingCartResultDTO(
    val id: UUID?,
    val total: BigDecimal?,
    val payed: Boolean,
    val userId: UUID?,
    val items: List<ProductShoppingCartResultDTO>?,
) {

    companion object {
        @JvmStatic
        fun from(shoppingCart: ShoppingCart): ShoppingCartResultDTO = ShoppingCartResultDTO(
            shoppingCart.id!!.value,
            shoppingCart.total,
            shoppingCart.payed,
            shoppingCart.userId!!.value,
            shoppingCart.items!!.map { ProductShoppingCartResultDTO.from(it) },
        )
    }
}
