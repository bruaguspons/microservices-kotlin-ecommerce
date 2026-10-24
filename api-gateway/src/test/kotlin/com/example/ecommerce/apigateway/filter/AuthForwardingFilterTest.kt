package com.example.ecommerce.apigateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.stream.Stream

class AuthForwardingFilterTest {

    private val filter = AuthForwardingFilter()
    private val chain: GatewayFilterChain = mock()

    @ParameterizedTest(name = "{0} {1} is public")
    @MethodSource("publicRoutes")
    fun publicRoutesPassWithoutAuthorizationHeader(method: HttpMethod, path: String) {
        val exchange = exchange(method, path)
        whenever(chain.filter(exchange)).thenReturn(Mono.empty())

        filter.filter(exchange, chain).block()

        verify(chain).filter(exchange)
        assertThat(exchange.response.statusCode).isNull()
    }

    @ParameterizedTest(name = "{0} {1} without header is rejected")
    @MethodSource("protectedRoutes")
    fun protectedRoutesWithoutHeaderAreRejectedWithExactJson(method: HttpMethod, path: String) {
        val exchange = exchange(method, path)

        filter.filter(exchange, chain).block()

        verify(chain, never()).filter(any<ServerWebExchange>())
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exchange.response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(exchange.response.bodyAsString.block()).isEqualTo(UNAUTHORIZED_BODY)
    }

    @Test
    fun protectedRouteWithAuthorizationHeaderIsForwardedUnmodified() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/shopping-carts").header("Authorization", "Bearer abc.def.ghi"),
        )
        whenever(chain.filter(exchange)).thenReturn(Mono.empty())

        filter.filter(exchange, chain).block()

        val captor = argumentCaptor<ServerWebExchange>()
        verify(chain).filter(captor.capture())
        val forwarded = captor.firstValue.request
        assertThat(captor.firstValue).isSameAs(exchange)
        assertThat(forwarded.headers.getFirst("Authorization")).isEqualTo("Bearer abc.def.ghi")
        assertThat(exchange.response.statusCode).isNull()
    }

    @Test
    fun headerPresenceIsEnoughTokenIsNeverDecoded() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/shopping-carts").header("authorization", "garbage"),
        )
        whenever(chain.filter(exchange)).thenReturn(Mono.empty())

        filter.filter(exchange, chain).block()

        verify(chain).filter(exchange)
    }

    @Test
    fun queryStringDoesNotAffectPublicRouteMatching() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products?category=PHONE"))
        whenever(chain.filter(exchange)).thenReturn(Mono.empty())

        filter.filter(exchange, chain).block()

        verify(chain).filter(exchange)
    }

    @Test
    fun runsFirst() {
        assertThat(filter.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE)
    }

    companion object {
        private const val UNAUTHORIZED_BODY =
            "{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Missing Authorization header\"}"

        private fun exchange(method: HttpMethod, path: String): MockServerWebExchange =
            MockServerWebExchange.from(MockServerHttpRequest.method(method, path))

        @JvmStatic
        fun publicRoutes(): Stream<Arguments> = Stream.of(
            Arguments.of(HttpMethod.GET, "/.well-known/jwks.json"),
            Arguments.of(HttpMethod.POST, "/api/auth/login"),
            Arguments.of(HttpMethod.POST, "/api/users"),
            Arguments.of(HttpMethod.GET, "/api/products"),
            Arguments.of(HttpMethod.GET, "/api/products/11111111-1111-1111-1111-111111111111"),
            Arguments.of(HttpMethod.GET, "/api/products/a/b"),
        )

        @JvmStatic
        fun protectedRoutes(): Stream<Arguments> = Stream.of(
            Arguments.of(HttpMethod.GET, "/api/shopping-carts"),
            Arguments.of(HttpMethod.POST, "/api/shopping-carts/1/pay"),
            Arguments.of(HttpMethod.GET, "/api/users"),
            Arguments.of(HttpMethod.GET, "/api/users/1"),
            Arguments.of(HttpMethod.POST, "/api/users/1"),
            Arguments.of(HttpMethod.POST, "/api/products"),
            Arguments.of(HttpMethod.PUT, "/api/products/1"),
            Arguments.of(HttpMethod.DELETE, "/api/products/1"),
            Arguments.of(HttpMethod.GET, "/api/auth/login"),
            Arguments.of(HttpMethod.POST, "/api/admin/users"),
            Arguments.of(HttpMethod.POST, "/.well-known/jwks.json"),
        )
    }
}
