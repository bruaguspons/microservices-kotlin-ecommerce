package com.example.ecommerce.shared.domain.valueobject

data class Email(val value: String?) {

    init {
        if (value == null || value.isBlank()) throw IllegalArgumentException("Email cannot be null or blank")
        if (!value.matches(EMAIL_REGEX)) throw IllegalArgumentException("Email is not valid")
    }

    // Record-style toString on purpose: it leaks into 404 error messages.
    override fun toString(): String = "Email[value=$value]"

    companion object {
        private val EMAIL_REGEX = Regex(
            "[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?",
            RegexOption.IGNORE_CASE,
        )

        @JvmStatic
        fun of(email: String?): Email = Email(email)
    }
}
