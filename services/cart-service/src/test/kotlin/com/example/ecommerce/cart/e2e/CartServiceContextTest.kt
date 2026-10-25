package com.example.ecommerce.cart.e2e

import com.example.ecommerce.buildingblocks.outbox.OutboxPublisherJob
import com.example.ecommerce.cart.application.exception.UserNotFoundException
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.application.port.out.client.ProductClientPort
import com.example.ecommerce.cart.application.port.out.client.ProductPriceResultDTO
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.config.DataSeeder
import com.example.ecommerce.cart.infrastructure.security.OwnershipGuard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

/**
 * Full context on H2. Cross-service client ports, the dev seeder and the Rabbit publisher job are mocked,
 * and Rabbit listeners are not started, so no network is used.
 */
@SpringBootTest(properties = ["spring.rabbitmq.listener.simple.auto-startup=false"])
@AutoConfigureMockMvc
class CartServiceContextTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var cartService: ShoppingCartServicePort

    @Autowired
    private lateinit var ownershipGuard: OwnershipGuard

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var productClient: ProductClientPort

    @MockitoBean
    private lateinit var userClient: UserClientPort

    @MockitoBean
    private lateinit var dataSeeder: DataSeeder

    @MockitoBean
    private lateinit var outboxPublisherJob: OutboxPublisherJob

    private fun asRole(role: String, subject: UUID): RequestPostProcessor =
        jwt().jwt { it.subject(subject.toString()) }.authorities(GrantedAuthority { "ROLE_$role" })

    private fun json(body: String): JsonNode = objectMapper.readTree(body)

    private fun createCart(user: UUID): UUID {
        whenever(userClient.exists(any<UserId>())).thenReturn(true)
        val body = mockMvc.perform(
            post("/api/shopping-carts").with(asRole("USER", user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user + "\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.payed").value(false))
            .andExpect(jsonPath("\$.total").value(0))
            .andExpect(jsonPath("\$.userId").value(user.toString()))
            .andExpect(jsonPath("\$.items").isEmpty())
            .andReturn().response.contentAsString
        return UUID.fromString(json(body).get("id").asString())
    }

    private fun addItem(user: UUID, cart: UUID, product: UUID, qty: Int, price: String, version: Long): String {
        whenever(productClient.getById(any<ProductId>())).thenReturn(ProductPriceResultDTO(product, BigDecimal(price), version))
        val body = mockMvc.perform(
            post("/api/shopping-carts/{c}/items", cart).with(asRole("USER", user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product + "\",\"quantity\":" + qty + "}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.quantity").value(qty))
            .andExpect(jsonPath("\$.productId").value(product.toString()))
            .andReturn().response.contentAsString
        return json(body).get("id").asString()
    }

    @Test
    fun servicesAndOwnershipGuardAreAopProxies() {
        assertThat(AopUtils.isAopProxy(cartService)).isTrue()
        assertThat(AopUtils.isAopProxy(ownershipGuard)).isTrue()
    }

    @Test
    fun cartLifecycleOwnerCanCreateAddUpdateRemoveAndPay() {
        val user = UUID.randomUUID()
        val product = UUID.randomUUID()
        val cart = createCart(user)

        val itemId = addItem(user, cart, product, 2, "10.50", 3L)
        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("USER", user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.total").value(21.00))
            .andExpect(jsonPath("\$.items.length()").value(1))

        mockMvc.perform(
            patch("/api/shopping-carts/{c}/items/{i}", cart, itemId).with(asRole("USER", user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.quantity").value(3))
            .andExpect(jsonPath("\$.unitPrice").value(10.50))
        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("USER", user)))
            .andExpect(jsonPath("\$.total").value(31.50))
        assertThat(jdbc.queryForObject("select price_version from product_shopping_cart where id = ?", Long::class.javaObjectType, UUID.fromString(itemId)))
            .isEqualTo(3L)

        mockMvc.perform(delete("/api/shopping-carts/{c}/items/{i}", cart, itemId).with(asRole("USER", user)))
            .andExpect(status().isNoContent())
        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("USER", user)))
            .andExpect(jsonPath("\$.total").value(0))
            .andExpect(jsonPath("\$.items").isEmpty())

        // Unknown item in the cart
        mockMvc.perform(delete("/api/shopping-carts/{c}/items/{i}", cart, itemId).with(asRole("USER", user)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("PRODUCT_SHOPPING_CART_NOT_FOUND"))
            .andExpect(jsonPath("\$.message").value("Item not found in cart with id: " + itemId))
    }

    @Test
    fun payWritesCartPaidOutboxRowAndBlocksFurtherChanges() {
        val user = UUID.randomUUID()
        val cart = createCart(user)
        addItem(user, cart, UUID.randomUUID(), 1, "7.00", 1L)

        mockMvc.perform(post("/api/shopping-carts/{c}/pay", cart).with(asRole("USER", user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.payed").value(true))
            .andExpect(jsonPath("\$.total").value(7.00))

        val payloads = jdbc.queryForList(
            "select payload from outbox_message where event_type = 'cart.paid' and payload like ?", String::class.java, "%" + cart + "%",
        )
        assertThat(payloads).hasSize(1)
        assertThat(payloads[0]).contains("\"cartId\":\"" + cart + "\"")
            .contains("\"userId\":\"" + user + "\"")
            .contains("\"totalAmount\":7.00")
            .contains("\"paidAt\":\"")
        assertThat(jdbc.queryForObject("select status from outbox_message where payload like ?", String::class.java, "%" + cart + "%"))
            .isEqualTo("PENDING")

        mockMvc.perform(post("/api/shopping-carts/{c}/pay", cart).with(asRole("USER", user)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("\$.errorCode").value("CART_ALREADY_PAID"))
            .andExpect(jsonPath("\$.message").value("Cannot modify a shopping cart that has already been paid"))
        whenever(productClient.getById(any<ProductId>())).thenReturn(ProductPriceResultDTO(UUID.randomUUID(), BigDecimal.ONE, 1L))
        mockMvc.perform(
            post("/api/shopping-carts/{c}/items", cart).with(asRole("USER", user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"),
        )
            .andExpect(status().isConflict())
        assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_message where event_type = 'cart.paid' and payload like ?",
                Int::class.javaObjectType, "%" + cart + "%",
            ),
        ).isEqualTo(1)
    }

    @Test
    fun ownershipIsEnforcedByMethodSecurity() {
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val cart = createCart(owner)

        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("USER", stranger))).andExpect(status().isForbidden())
        mockMvc.perform(post("/api/shopping-carts/{c}/pay", cart).with(asRole("USER", stranger))).andExpect(status().isForbidden())
        mockMvc.perform(delete("/api/shopping-carts/{c}", cart).with(asRole("USER", stranger))).andExpect(status().isForbidden())
        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("ADMIN", stranger))).andExpect(status().isOk())
        mockMvc.perform(get("/api/shopping-carts").with(asRole("USER", owner))).andExpect(status().isForbidden())
        mockMvc.perform(get("/api/shopping-carts").with(asRole("ADMIN", stranger))).andExpect(status().isOk())
        mockMvc.perform(
            post("/api/shopping-carts").with(asRole("USER", stranger)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + owner + "\"}"),
        )
            .andExpect(status().isForbidden())
    }

    @Test
    fun unknownCartIsNotFoundForAdmin() {
        mockMvc.perform(get("/api/shopping-carts/{c}", UUID.randomUUID()).with(asRole("ADMIN", UUID.randomUUID())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("SHOPPING_CART_NOT_FOUND"))
            .andExpect(jsonPath("\$.message").value("Shopping cart not found"))
    }

    @Test
    fun unknownCartIsForbiddenForNonAdminBecauseOwnershipCheckFailsFirst() {
        mockMvc.perform(get("/api/shopping-carts/{c}", UUID.randomUUID()).with(asRole("USER", UUID.randomUUID())))
            .andExpect(status().isForbidden())
    }

    @Test
    fun userClientNotFoundPropagatesAsNotFoundOnCreate() {
        val user = UUID.randomUUID()
        whenever(userClient.exists(any<UserId>())).thenThrow(UserNotFoundException("User not found with id: " + user))

        mockMvc.perform(
            post("/api/shopping-carts").with(asRole("USER", user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user + "\"}"),
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("USER_NOT_FOUND"))
    }

    @Test
    fun createFailsWithUserNotFoundWhenUserDoesNotExist() {
        val user = UUID.randomUUID()
        whenever(userClient.exists(any<UserId>())).thenReturn(false)

        mockMvc.perform(
            post("/api/shopping-carts").with(asRole("USER", user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user + "\"}"),
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("USER_NOT_FOUND"))

        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where user_id = ?", Int::class.javaObjectType, user)).isZero()
    }

    @Test
    fun deleteSoftDeletesCartAndHidesItFromReads() {
        val user = UUID.randomUUID()
        val cart = createCart(user)

        mockMvc.perform(delete("/api/shopping-carts/{c}", cart).with(asRole("USER", user))).andExpect(status().isNoContent())

        assertThat(jdbc.queryForObject("select is_deleted from shopping_cart where id = ?", Boolean::class.javaObjectType, cart)).isTrue()
        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("ADMIN", user))).andExpect(status().isNotFound())
    }

    @Test
    fun malformedCartIdReturnsInvalidArgumentBody() {
        mockMvc.perform(get("/api/shopping-carts/not-a-uuid").with(asRole("ADMIN", UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("\$.errorCode").value("INVALID_ARGUMENT"))
    }

    @Test
    fun productPriceChangedHandlerUpdatesPriceOfOpenCarts() {
        val user = UUID.randomUUID()
        val product = UUID.randomUUID()
        val cart = createCart(user)
        addItem(user, cart, product, 2, "10.00", 5L)

        cartService.handleProductPriceChanged(product, BigDecimal("12.00"), 6L)

        mockMvc.perform(get("/api/shopping-carts/{c}", cart).with(asRole("USER", user)))
            .andExpect(jsonPath("\$.total").value(24.00))
            .andExpect(jsonPath("\$.items[0].unitPrice").value(12.00))
    }

    @Test
    fun productPriceChangedWithoutProductIdIsASilentNoOp() {
        cartService.handleProductPriceChanged(null, BigDecimal("1.00"), 1L)
    }

    @Test
    fun handleProductPriceChangedWithNoAffectedCartsIsANoOp() {
        cartService.handleProductPriceChanged(UUID.randomUUID(), BigDecimal("1.00"), 1L)
    }

    @Test
    fun userDeletedEventHandlingIsANoOp() {
        val before: Long = jdbc.queryForObject("select count(*) from shopping_cart", Long::class.javaObjectType)!!

        cartService.handleUserDeleted(UUID.randomUUID())

        assertThat(jdbc.queryForObject("select count(*) from shopping_cart", Long::class.javaObjectType)).isEqualTo(before)
    }
}
