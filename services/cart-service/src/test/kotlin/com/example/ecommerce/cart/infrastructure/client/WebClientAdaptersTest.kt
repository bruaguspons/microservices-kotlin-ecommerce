package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.cart.application.exception.ProductNotFoundException
import com.example.ecommerce.cart.application.exception.ProductUnavailableException
import com.example.ecommerce.cart.application.exception.UserNotFoundException
import com.example.ecommerce.cart.application.exception.UserUnavailableException
import com.example.ecommerce.cart.application.port.out.client.ProductPriceResultDTO
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.UserId
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.net.ConnectException
import java.net.URI
import java.util.UUID

/** Adapter exception mapping, with the HTTP client beans mocked. */
class WebClientAdaptersTest {

    private val productHttp: ProductServiceHttpClient = mock()
    private val userHttp: UserServiceHttpClient = mock()
    private val productAdapter = ProductWebClientAdapter(productHttp)
    private val userAdapter = UserWebClientAdapter(userHttp)

    private fun connectFailure(): WebClientRequestException =
        WebClientRequestException(ConnectException("refused"), HttpMethod.GET, URI.create("http://x/y"), HttpHeaders())

    private fun openCircuit(): CallNotPermittedException =
        CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x"))

    @Test
    fun productSuccessIsPassedThrough() {
        val dto = ProductPriceResultDTO(PRODUCT, BigDecimal("9.99"), 4L)
        whenever(productHttp.fetch(ProductId.of(PRODUCT))).thenReturn(dto)

        assertThat(productAdapter.getById(ProductId.of(PRODUCT))).isSameAs(dto)
    }

    @Test
    fun product404MapsToNotFoundWithIdValueInMessage() {
        whenever(productHttp.fetch(any<ProductId>())).thenThrow(WebClientResponseException.create(404, "Not Found", HttpHeaders(), ByteArray(0), null))

        assertThatThrownBy { productAdapter.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductNotFoundException::class.java)
            .hasMessage("Product not found with id: " + PRODUCT)
    }

    @Test
    fun productOtherHttpErrorMapsToUnavailable() {
        whenever(productHttp.fetch(any<ProductId>())).thenThrow(WebClientResponseException.create(500, "Server Error", HttpHeaders(), ByteArray(0), null))

        assertThatThrownBy { productAdapter.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
            .hasMessageStartingWith("product-service is unavailable: 500")
    }

    @Test
    fun productConnectionFailureMapsToUnavailable() {
        whenever(productHttp.fetch(any<ProductId>())).thenThrow(connectFailure())

        assertThatThrownBy { productAdapter.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
            .hasMessageStartingWith("product-service is unavailable: ")
    }

    @Test
    fun productOpenCircuitMapsToUnavailable() {
        whenever(productHttp.fetch(any<ProductId>())).thenThrow(openCircuit())

        assertThatThrownBy { productAdapter.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
    }

    @Test
    fun productAnyOtherRuntimeExceptionMapsToUnavailable() {
        whenever(productHttp.fetch(any<ProductId>())).thenThrow(IllegalStateException("Timeout on blocking read"))

        assertThatThrownBy { productAdapter.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
            .hasMessage("product-service is unavailable: Timeout on blocking read")
    }

    @Test
    fun userExistsPassesThrough() {
        whenever(userHttp.exists(UserId.of(USER))).thenReturn(true)

        assertThat(userAdapter.exists(UserId.of(USER))).isTrue()
    }

    @Test
    fun user404MapsToNotFound() {
        whenever(userHttp.exists(any<UserId>())).thenThrow(WebClientResponseException.create(404, "Not Found", HttpHeaders(), ByteArray(0), null))

        assertThatThrownBy { userAdapter.exists(UserId.of(USER)) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found with id: " + USER)
    }

    @Test
    fun userFailuresMapToUnavailable() {
        doThrow(connectFailure()).whenever(userHttp).exists(any<UserId>())
        assertThatThrownBy { userAdapter.exists(UserId.of(USER)) }.isInstanceOf(UserUnavailableException::class.java)

        doThrow(openCircuit()).whenever(userHttp).exists(any<UserId>())
        assertThatThrownBy { userAdapter.exists(UserId.of(USER)) }.isInstanceOf(UserUnavailableException::class.java)

        doThrow(WebClientResponseException.create(403, "Forbidden", HttpHeaders(), ByteArray(0), null)).whenever(userHttp).exists(any<UserId>())
        assertThatThrownBy { userAdapter.exists(UserId.of(USER)) }
            .isInstanceOf(UserUnavailableException::class.java)
            .hasMessageStartingWith("user-service is unavailable: 403")
    }

    companion object {
        private val PRODUCT: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001")
        private val USER: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    }
}
