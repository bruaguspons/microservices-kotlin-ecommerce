package com.example.ecommerce.apigateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Mandatory Authorization-header-presence check for every route, except an
 * explicit public-route allowlist. Per design doc: the gateway enforces
 * "must have Authorization header"; each downstream service independently
 * validates the JWT's signature/expiry (defense-in-depth) - this filter does
 * NOT decode or validate the token itself, only checks the header is present,
 * and forwards it unmodified when present.
 */
@Component
class AuthForwardingFilter : GlobalFilter, Ordered {

    private data class PublicRoute(val method: HttpMethod, val pathPattern: String)

    private val pathMatcher = AntPathMatcher()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request = exchange.request

        if (isPublicRoute(request)) {
            return chain.filter(exchange)
        }

        if (request.headers.getFirst(HttpHeaders.AUTHORIZATION) == null) {
            log.warn(
                "gateway_reject_missing_authorization path={} method={}",
                request.uri.path, request.method,
            )
            return rejectUnauthorized(exchange)
        }

        return chain.filter(exchange)
    }

    private fun isPublicRoute(request: ServerHttpRequest): Boolean {
        val path = request.uri.path
        val method = request.method
        return PUBLIC_ROUTES.any { route -> route.method == method && pathMatcher.match(route.pathPattern, path) }
    }

    private fun rejectUnauthorized(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        val buffer = response.bufferFactory().wrap(UNAUTHORIZED_BODY.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    private companion object {
        private val log = LoggerFactory.getLogger(AuthForwardingFilter::class.java)

        /**
         * Public allowlist per design/spec: JWKS discovery, login, and
         * registration must be reachable without an existing token.
         */
        private val PUBLIC_ROUTES = listOf(
            PublicRoute(HttpMethod.GET, "/.well-known/jwks.json"),
            PublicRoute(HttpMethod.POST, "/api/auth/login"),
            PublicRoute(HttpMethod.POST, "/api/users"),
            PublicRoute(HttpMethod.GET, "/api/products"),
            PublicRoute(HttpMethod.GET, "/api/products/**"),
        )

        private const val UNAUTHORIZED_BODY =
            "{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Missing Authorization header\"}"
    }
}
