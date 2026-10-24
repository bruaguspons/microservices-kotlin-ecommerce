package com.example.ecommerce.cart.domain.valueobject

import java.util.UUID

data class ShoppingCartId(val value: UUID?) {

    // Record-style toString on purpose: it leaks into error messages.
    override fun toString(): String = "ShoppingCartId[value=$value]"

    companion object {
        @JvmStatic
        fun of(value: UUID?): ShoppingCartId = ShoppingCartId(value)

        @JvmStatic
        fun of(value: String): ShoppingCartId = ShoppingCartId(UUID.fromString(value))

        @JvmStatic
        fun emptyValue(): ShoppingCartId = ShoppingCartId(null)
    }
}
