package com.example.ecommerce.cart.domain.valueobject

import java.util.UUID

data class ProductId(val value: UUID?) {

    // Record-style toString on purpose: it leaks into error messages.
    override fun toString(): String = "ProductId[value=$value]"

    companion object {
        @JvmStatic
        fun of(value: UUID?): ProductId = ProductId(value)

        @JvmStatic
        fun of(value: String): ProductId = ProductId(UUID.fromString(value))

        @JvmStatic
        fun emptyValue(): ProductId = ProductId(null)
    }
}
