package com.example.ecommerce.cart.domain.valueobject

import java.util.UUID

data class ProductShoppingCartId(val value: UUID?) {

    // Record-style toString on purpose: it leaks into error messages.
    override fun toString(): String = "ProductShoppingCartId[value=$value]"

    companion object {
        @JvmStatic
        fun of(value: UUID?): ProductShoppingCartId = ProductShoppingCartId(value)

        @JvmStatic
        fun of(value: String): ProductShoppingCartId = ProductShoppingCartId(UUID.fromString(value))

        @JvmStatic
        fun emptyValue(): ProductShoppingCartId = ProductShoppingCartId(null)
    }
}
