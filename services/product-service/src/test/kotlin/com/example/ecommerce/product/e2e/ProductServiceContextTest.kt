package com.example.ecommerce.product.e2e

import com.example.ecommerce.buildingblocks.outbox.OutboxPublisherJob
import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.dto.command.UpdateProductCommandDTO
import com.example.ecommerce.product.application.dto.result.ProductResultDTO
import com.example.ecommerce.product.application.port.`in`.ProductServicePort
import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.persistence.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

/** Full context (H2, dev profile seeder active). No RabbitMQ connection is made: the publisher job is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceContextTest {

    @Autowired
    private lateinit var productService: ProductServicePort

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var outboxPublisherJob: OutboxPublisherJob

    private fun command(name: String, price: String): CreateProductCommandDTO =
        CreateProductCommandDTO(name, "desc", BigDecimal(price), "/img.jpg", ProductCategory.PHONE)

    @Test
    fun serviceAndRepositoryAreAopProxies() {
        assertThat(AopUtils.isAopProxy(productService)).isTrue()
        assertThat(AopUtils.isAopProxy(productRepository)).isTrue() // @Repository exception translation proxy
    }

    @Test
    fun createWithoutAuthenticationIsRejected() {
        assertThatThrownBy { productService.create(command("x", "1.00")) }
            .isInstanceOf(AuthenticationCredentialsNotFoundException::class.java)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun createWithUserRoleIsDenied() {
        assertThatThrownBy { productService.create(command("x", "1.00")) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun createWithAdminRoleSucceeds() {
        val created = productService.create(command("ctx-admin", "1.00"))

        assertThat(created.id).isNotNull()
        assertThat(created.version).isNotNull()
    }

    @Test
    fun readsArePublicAtServiceLevel() {
        assertThat(productService.findAll()).isNotNull()
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun priceChangeWritesOutboxRowInSameTransaction() {
        val created: ProductResultDTO = productService.create(command("outbox-price", "10.00"))
        jdbc.update("delete from outbox_message")

        productService.update(
            ProductId(created.id),
            UpdateProductCommandDTO("outbox-price", "desc", BigDecimal("12.00"), "/img.jpg", ProductCategory.PHONE),
        )

        val types = jdbc.queryForList("select event_type from outbox_message where status = 'PENDING'", String::class.java)
        assertThat(types).containsExactly("product.price-changed")
        val payload = jdbc.queryForObject("select payload from outbox_message", String::class.java)
        assertThat(payload).contains("\"productId\":\"" + created.id + "\"").contains("\"newPrice\":12.00")
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun sameOrEqualPriceUpdateWritesNoOutboxRow() {
        val created = productService.create(command("outbox-same", "10.00"))
        jdbc.update("delete from outbox_message")

        productService.update(
            ProductId(created.id),
            UpdateProductCommandDTO("renamed", "desc", BigDecimal("10.000"), "/img.jpg", ProductCategory.PHONE),
        )

        assertThat(jdbc.queryForObject("select count(*) from outbox_message", Int::class.javaObjectType)).isZero()
    }

    @Test
    fun restCrudThroughRealStackAndDeleteBehavior() {
        val createResult = mockMvc.perform(
            post("/api/products").with(jwt().authorities(GrantedAuthority { "ROLE_ADMIN" }))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"e2e\",\"desc\":\"d\",\"price\":3.25,\"imagePath\":\"/i.jpg\",\"category\":\"COMPUTER\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.name").value("e2e"))
            .andExpect(jsonPath("\$.category").value("COMPUTER"))
            .andReturn()
        val id = objectMapper.readTree(createResult.response.contentAsString).get("id").asString()

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("\$.id").value(id))

        mockMvc.perform(delete("/api/products/{id}", id).with(jwt().authorities(GrantedAuthority { "ROLE_ADMIN" })))
            .andExpect(status().isNoContent())

        mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("PRODUCT_NOT_FOUND"))
            .andExpect(jsonPath("\$.message").value("Product not found with id: ProductId[value=" + id + "]"))
        assertThat(
            jdbc.queryForObject("select is_deleted from products where id = ?", Boolean::class.javaObjectType, UUID.fromString(id)),
        ).isTrue()
    }

    @Test
    fun adminRoleIsEnforcedByMethodSecurityThroughRestLayer() {
        mockMvc.perform(
            post("/api/products").with(jwt().authorities(GrantedAuthority { "ROLE_USER" }))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"price\":1,\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isForbidden())
        mockMvc.perform(
            put("/api/products/{id}", UUID.randomUUID()).with(jwt().authorities(GrantedAuthority { "ROLE_USER" }))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"price\":1,\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isForbidden())
    }

    @Test
    fun devSeederPopulatesFifteenProducts() {
        assertThat(
            jdbc.queryForObject(
                "select count(*) from products where name not like 'e2e%' and name not like 'ctx-%' and name not like 'outbox-%' and name <> 'renamed'",
                Int::class.javaObjectType,
            ),
        )
            .isGreaterThanOrEqualTo(15)
    }
}
