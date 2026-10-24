package com.example.ecommerce.cart.domain.valueobject

import java.util.UUID

data class UserId(val value: UUID?) {

    // Record-style toString on purpose: it leaks into error messages.
    override fun toString(): String = "UserId[value=$value]"

    companion object {
        @JvmStatic
        fun of(value: UUID?): UserId = UserId(value)

        @JvmStatic
        fun of(value: String): UserId = UserId(UUID.fromString(value))

        @JvmStatic
        fun emptyValue(): UserId = UserId(null)
    }
}
