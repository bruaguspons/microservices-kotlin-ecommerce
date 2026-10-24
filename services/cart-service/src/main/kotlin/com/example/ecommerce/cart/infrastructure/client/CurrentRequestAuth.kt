package com.example.ecommerce.cart.infrastructure.client

import org.springframework.http.HttpHeaders
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Extracts the incoming request's `Authorization` header so outbound
 * WebClient calls to product-service/user-service forward it unmodified
 * (see design: Sync Communication - the caller's identity/authorization is
 * propagated downstream, each service still independently validates it).
 */
internal object CurrentRequestAuth {

    @JvmStatic
    fun header(): String? {
        val attrs = RequestContextHolder.getRequestAttributes()
        if (attrs !is ServletRequestAttributes) {
            return null
        }
        return attrs.request.getHeader(HttpHeaders.AUTHORIZATION)
    }
}
