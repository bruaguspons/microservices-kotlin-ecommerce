package com.example.ecommerce.cart.application.dto.result

import com.example.ecommerce.cart.domain.model.ProductShoppingCart
import java.math.BigDecimal
import java.util.UUID

data class ProductShoppingCartResultDTO(
    val id: UUID?,
    val productId: UUID?,
    val shoppingCartId: UUID?,
    val quantity: Int?,
    val unitPrice: BigDecimal?,
) {

    companion object {
        @JvmStatic
        fun from(item: ProductShoppingCart): ProductShoppingCartResultDTO = ProductShoppingCartResultDTO(
            item.id!!.value,
            item.productId!!.value,
            item.shoppingCartId!!.value,
            item.quantity,
            item.unitPrice,
        )
    }
}
