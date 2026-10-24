package com.example.ecommerce.cart.infrastructure.client

import com.example.ecommerce.buildingblocks.outbox.OutboxPublisherJob
import com.example.ecommerce.cart.application.exception.ProductNotFoundException
import com.example.ecommerce.cart.application.exception.ProductUnavailableException
import com.example.ecommerce.cart.application.exception.UserNotFoundException
import com.example.ecommerce.cart.application.exception.UserUnavailableException
import com.example.ecommerce.cart.application.port.out.client.ProductClientPort
import com.example.ecommerce.cart.application.port.out.client.ProductPriceResultDTO
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.config.DataSeeder
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Real adapters, real Resilience4j configuration from application.yml, and an in-memory ExchangeFunction
 * standing in for product-service/user-service (no sockets are opened).
 * Retry back-off from the yml (100ms, x2) applies to the retried scenarios.
 */
@SpringBootTest(properties = ["spring.rabbitmq.listener.simple.auto-startup=false"])
class ResilienceProxyIntegrationTest {

    @TestBean(name = "productServiceWebClient", methodName = "stubProductWebClient")
    lateinit var productServiceWebClient: WebClient

    @TestBean(name = "userServiceWebClient", methodName = "stubUserWebClient")
    lateinit var userServiceWebClient: WebClient

    @MockitoBean
    private lateinit var dataSeeder: DataSeeder

    @MockitoBean
    private lateinit var outboxPublisherJob: OutboxPublisherJob

    @Autowired
    private lateinit var productClient: ProductClientPort

    @Autowired
    private lateinit var userClient: UserClientPort

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var retryRegistry: RetryRegistry

    @BeforeEach
    fun reset() {
        PRODUCT_CALLS.set(0)
        USER_CALLS.set(0)
        LAST_AUTH.set(null)
        circuitBreakerRegistry.circuitBreaker("product-service").reset()
        circuitBreakerRegistry.circuitBreaker("user-service").reset()
    }

    @AfterEach
    fun clearRequest() {
        RequestContextHolder.resetRequestAttributes()
    }

    // ---- proxies and configuration -------------------------------------------------------------------------

    @Test
    fun httpClientBeansAreAopProxies() {
        assertThat(AopUtils.isAopProxy(context.getBean("productServiceHttpClient"))).isTrue()
        assertThat(AopUtils.isAopProxy(context.getBean("userServiceHttpClient"))).isTrue()
    }

    @Test
    fun circuitBreakerConfigurationComesFromYml() {
        for (name in arrayOf("product-service", "user-service")) {
            val config = circuitBreakerRegistry.circuitBreaker(name).circuitBreakerConfig
            assertThat(config.slidingWindowType).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            assertThat(config.slidingWindowSize).isEqualTo(10)
            assertThat(config.minimumNumberOfCalls).isEqualTo(5)
            assertThat(config.failureRateThreshold).isEqualTo(50f)
            assertThat(config.permittedNumberOfCallsInHalfOpenState).isEqualTo(3)
            assertThat(config.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(Duration.ofSeconds(30).toMillis())
        }
        val productCfg = circuitBreakerRegistry.circuitBreaker("product-service").circuitBreakerConfig
        val userCfg = circuitBreakerRegistry.circuitBreaker("user-service").circuitBreakerConfig
        assertThat(productCfg.ignoreExceptionPredicate.test(ProductNotFoundException("x"))).isTrue()
        assertThat(userCfg.ignoreExceptionPredicate.test(UserNotFoundException("x"))).isTrue()
    }

    @Test
    fun retryConfigurationComesFromYml() {
        for (name in arrayOf("product-service", "user-service")) {
            val retry: Retry = retryRegistry.retry(name)
            assertThat(retry.retryConfig.maxAttempts).isEqualTo(3)
            assertThat(retry.retryConfig.getIntervalBiFunction<Any?>().apply(1, Either.right<Throwable, Any?>(null))).isEqualTo(100L)
            assertThat(retry.retryConfig.getIntervalBiFunction<Any?>().apply(2, Either.right<Throwable, Any?>(null))).isEqualTo(200L)
            // WebClientResponseException.NotFound is configured as an ignored (non-retried) exception.
            val notFound = WebClientResponseException.create(404, "Not Found", HttpHeaders(), ByteArray(0), null)
            assertThat(retry.retryConfig.exceptionPredicate.test(notFound)).isFalse()
            val serverError = WebClientResponseException.create(500, "Error", HttpHeaders(), ByteArray(0), null)
            assertThat(retry.retryConfig.exceptionPredicate.test(serverError)).isTrue()
        }
    }

    // ---- product-service client ----------------------------------------------------------------------------

    @Test
    fun productSuccessMapsPriceAndVersionAndForwardsAuthorizationHeader() {
        PRODUCT_HANDLER.set { json(HttpStatus.OK, "{\"id\":\"" + PRODUCT + "\",\"name\":\"n\",\"desc\":\"d\",\"price\":10.50,\"imagePath\":\"i\",\"category\":\"PHONE\",\"version\":4}") }
        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

        val result = productClient.getById(ProductId.of(PRODUCT))

        assertThat(result).isEqualTo(ProductPriceResultDTO(PRODUCT, BigDecimal("10.50"), 4L))
        assertThat(LAST_AUTH.get()).isEqualTo("Bearer abc")
        assertThat(PRODUCT_CALLS).hasValue(1)
    }

    @Test
    fun productWithoutCurrentRequestSendsNoAuthorizationAndNullVersionBecomesZero() {
        PRODUCT_HANDLER.set { json(HttpStatus.OK, "{\"id\":\"" + PRODUCT + "\",\"price\":1.00}") }

        val result = productClient.getById(ProductId.of(PRODUCT))

        assertThat(result!!.version).isZero()
        assertThat(LAST_AUTH.get()).isNull()
    }

    @Test
    fun product404IsNotRetriedAndMapsToNotFound() {
        PRODUCT_HANDLER.set { status(HttpStatus.NOT_FOUND) }

        assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductNotFoundException::class.java)
            .hasMessage("Product not found with id: " + PRODUCT)

        assertThat(PRODUCT_CALLS).hasValue(1)
    }

    @Test
    fun product404sAreCountedAsCircuitFailuresCurrentBehavior() {
        // application.yml ignores cart's ProductNotFoundException, but the annotated bean throws
        // WebClientResponseException.NotFound, so 404 responses are recorded as failures.
        PRODUCT_HANDLER.set { status(HttpStatus.NOT_FOUND) }

        for (i in 0 until 5) {
            assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }.isInstanceOf(ProductNotFoundException::class.java)
        }

        val breaker = circuitBreakerRegistry.circuitBreaker("product-service")
        assertThat(breaker.metrics.numberOfFailedCalls).isEqualTo(5)
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)
        assertThat(PRODUCT_CALLS).hasValue(5)
    }

    @Test
    fun productServerErrorIsRetriedThreeTimesThenUnavailable() {
        PRODUCT_HANDLER.set { status(HttpStatus.INTERNAL_SERVER_ERROR) }

        assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
            .hasMessageStartingWith("product-service is unavailable: 500")

        assertThat(PRODUCT_CALLS).hasValue(3)
    }

    @Test
    fun productConnectionFailureIsRetriedThenUnavailable() {
        PRODUCT_HANDLER.set {
            Mono.error<ClientResponse>(
                WebClientRequestException(
                    ConnectException("refused"), HttpMethod.GET, URI.create("http://product-service.test/api/products/x"), HttpHeaders(),
                ),
            )
        }

        assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)

        assertThat(PRODUCT_CALLS).hasValue(3)
    }

    @Test
    fun productCircuitOpensAfterRepeatedFailuresAndShortCircuitsCalls() {
        PRODUCT_HANDLER.set { status(HttpStatus.INTERNAL_SERVER_ERROR) }

        // minimum-number-of-calls is 5; each invocation makes up to 3 attempts.
        for (i in 0 until 2) {
            assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }.isInstanceOf(ProductUnavailableException::class.java)
        }

        val breaker = circuitBreakerRegistry.circuitBreaker("product-service")
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)
        val callsWhenOpen = PRODUCT_CALLS.get()

        assertThatThrownBy { productClient.getById(ProductId.of(PRODUCT)) }
            .isInstanceOf(ProductUnavailableException::class.java)
            .hasMessageContaining("CircuitBreaker 'product-service' is OPEN and does not permit further calls")
        assertThat(PRODUCT_CALLS).hasValue(callsWhenOpen)
    }

    // ---- user-service client -------------------------------------------------------------------------------

    @Test
    fun userExistsReturnsTrueOn2xxAndForwardsAuthorization() {
        USER_HANDLER.set { status(HttpStatus.OK) }
        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

        assertThat(userClient.exists(UserId.of(USER))).isTrue()

        assertThat(LAST_AUTH.get()).isEqualTo("Bearer user-token")
        assertThat(USER_CALLS).hasValue(1)
    }

    @Test
    fun user404IsNotRetriedAndMapsToNotFound() {
        USER_HANDLER.set { status(HttpStatus.NOT_FOUND) }

        assertThatThrownBy { userClient.exists(UserId.of(USER)) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found with id: " + USER)

        assertThat(USER_CALLS).hasValue(1)
    }

    @Test
    fun userServerErrorIsRetriedThreeTimesThenUnavailable() {
        USER_HANDLER.set { status(HttpStatus.SERVICE_UNAVAILABLE) }

        assertThatThrownBy { userClient.exists(UserId.of(USER)) }
            .isInstanceOf(UserUnavailableException::class.java)
            .hasMessageStartingWith("user-service is unavailable: 503")

        assertThat(USER_CALLS).hasValue(3)
    }

    companion object {
        private val PRODUCT: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001")
        private val USER: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

        private val PRODUCT_CALLS = AtomicInteger()
        private val USER_CALLS = AtomicInteger()
        private val PRODUCT_HANDLER = AtomicReference<(ClientRequest) -> Mono<ClientResponse>>()
        private val USER_HANDLER = AtomicReference<(ClientRequest) -> Mono<ClientResponse>>()
        private val LAST_AUTH = AtomicReference<String>()

        @JvmStatic
        fun stubProductWebClient(): WebClient =
            WebClient.builder().baseUrl("http://product-service.test")
                .exchangeFunction { req ->
                    PRODUCT_CALLS.incrementAndGet()
                    LAST_AUTH.set(req.headers().getFirst(HttpHeaders.AUTHORIZATION))
                    PRODUCT_HANDLER.get().invoke(req)
                }.build()

        @JvmStatic
        fun stubUserWebClient(): WebClient =
            WebClient.builder().baseUrl("http://user-service.test")
                .exchangeFunction { req ->
                    USER_CALLS.incrementAndGet()
                    LAST_AUTH.set(req.headers().getFirst(HttpHeaders.AUTHORIZATION))
                    USER_HANDLER.get().invoke(req)
                }.build()

        private fun json(status: HttpStatus, body: String): Mono<ClientResponse> =
            Mono.just(ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build())

        private fun status(status: HttpStatus): Mono<ClientResponse> =
            Mono.just(ClientResponse.create(status).build())
    }
}
