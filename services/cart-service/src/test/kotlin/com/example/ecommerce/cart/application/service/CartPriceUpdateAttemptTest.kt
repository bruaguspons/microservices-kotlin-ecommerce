package com.example.ecommerce.cart.application.service

import com.example.ecommerce.buildingblocks.outbox.OutboxPublisherJob
import com.example.ecommerce.cart.application.exception.ShoppingCartNotFoundException
import com.example.ecommerce.cart.application.port.out.ShoppingCartRepositoryPort
import com.example.ecommerce.cart.application.port.out.client.ProductClientPort
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.config.DataSeeder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

/** Same context configuration as CartServiceContextTest so the Spring test context is reused. */
@SpringBootTest(properties = ["spring.rabbitmq.listener.simple.auto-startup=false"])
class CartPriceUpdateAttemptTest {

    @Autowired
    private lateinit var attempt: CartPriceUpdateAttempt

    @Autowired
    private lateinit var repository: ShoppingCartRepositoryPort

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var productClient: ProductClientPort

    @MockitoBean
    private lateinit var userClient: UserClientPort

    @MockitoBean
    private lateinit var dataSeeder: DataSeeder

    @MockitoBean
    private lateinit var outboxPublisherJob: OutboxPublisherJob

    @Test
    fun attemptBeanIsTransactionalProxy() {
        assertThat(AopUtils.isAopProxy(attempt)).isTrue()
    }

    @Test
    fun applyUpdatesPriceInsideItsOwnTransactionHonoringVersion() {
        val product = ProductId.of(UUID.randomUUID())
        // Repository reads need a session for the lazy items collection; the service layer provides it in production.
        val tx = TransactionTemplate(transactionManager)
        val cart: ShoppingCart = tx.execute { repository.save(ShoppingCart.create(UserId.of(UUID.randomUUID()))) }!!
        // item mapping needs a persisted cart id: add the line in a second transaction
        tx.executeWithoutResult {
            val loaded = repository.findById(cart.id!!).orElseThrow()
            loaded.addItem(product, 2, BigDecimal("10.00"), 5L)
            repository.save(loaded)
        }

        attempt.apply(cart.id!!, product, BigDecimal("99.00"), 4L)
        assertThat(jdbc.queryForObject("select total from shopping_cart where id = ?", BigDecimal::class.java, cart.id!!.value))
            .isEqualByComparingTo("20.00")

        attempt.apply(cart.id!!, product, BigDecimal("12.00"), 6L)
        assertThat(jdbc.queryForObject("select total from shopping_cart where id = ?", BigDecimal::class.java, cart.id!!.value))
            .isEqualByComparingTo("24.00")
        assertThat(jdbc.queryForObject("select unit_price from product_shopping_cart where shopping_cart_id = ?", BigDecimal::class.java, cart.id!!.value))
            .isEqualByComparingTo("12.00")
        assertThat(jdbc.queryForObject("select price_version from product_shopping_cart where shopping_cart_id = ?", Long::class.javaObjectType, cart.id!!.value))
            .isEqualTo(6L)
    }

    @Test
    fun applyOnMissingCartThrowsNotFoundWithRawUuidInMessage() {
        val missing = UUID.randomUUID()

        assertThatThrownBy { attempt.apply(ShoppingCartId.of(missing), ProductId.of(UUID.randomUUID()), BigDecimal.ONE, 1L) }
            .isInstanceOf(ShoppingCartNotFoundException::class.java)
            .hasMessage("Shopping cart not found with id: " + missing)
    }
}
