package com.example.ecommerce.cart.infrastructure.api

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import com.example.ecommerce.cart.application.dto.command.AddItemCommandDTO
import com.example.ecommerce.cart.application.dto.command.CreateShoppingCartDTO
import com.example.ecommerce.cart.application.dto.command.UpdateItemQuantityCommandDTO
import com.example.ecommerce.cart.application.dto.result.ProductShoppingCartResultDTO
import com.example.ecommerce.cart.application.dto.result.ShoppingCartResultDTO
import com.example.ecommerce.cart.application.exception.ProductNotFoundException
import com.example.ecommerce.cart.application.exception.ProductUnavailableException
import com.example.ecommerce.cart.application.exception.ShoppingCartNotFoundException
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.domain.exception.CartAlreadyPaidException
import com.example.ecommerce.cart.infrastructure.security.SecurityConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(controllers = [ShoppingCartController::class, ShoppingCartItemController::class])
@EnableWebSecurity
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class CartControllersContractTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var cartService: ShoppingCartServicePort

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var jwtAuthConverter: JwtAuthConverter

    @MockitoBean(name = "jpaMappingContext")
    private lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private fun cartDto(payed: Boolean): ShoppingCartResultDTO =
        ShoppingCartResultDTO(CART, BigDecimal("21.00"), payed, USER, listOf(itemDto()))

    private fun itemDto(): ProductShoppingCartResultDTO =
        ProductShoppingCartResultDTO(ITEM, PRODUCT, CART, 2, BigDecimal("10.50"))

    @Test
    fun everyEndpointRequiresAuthentication() {
        mockMvc.perform(get("/api/shopping-carts")).andExpect(status().isUnauthorized())
        mockMvc.perform(get("/api/shopping-carts/{id}", CART)).andExpect(status().isUnauthorized())
        mockMvc.perform(post("/api/shopping-carts/{id}/pay", CART)).andExpect(status().isUnauthorized())
        mockMvc.perform(post("/api/shopping-carts/{id}/items", CART)).andExpect(status().isUnauthorized())
        verifyNoInteractions(cartService)
    }

    @Test
    fun getByIdReturnsCartJson() {
        whenever(cartService.findById(CART)).thenReturn(cartDto(false))

        mockMvc.perform(get("/api/shopping-carts/{id}", CART).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string(CART_JSON))
    }

    @Test
    fun getAllReturnsArray() {
        whenever(cartService.findAll()).thenReturn(listOf(cartDto(false)))

        mockMvc.perform(get("/api/shopping-carts").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string("[" + CART_JSON + "]"))
    }

    @Test
    fun malformedCartIdIsBadRequest() {
        mockMvc.perform(get("/api/shopping-carts/not-a-uuid").with(jwt())).andExpect(status().isBadRequest())
        verifyNoInteractions(cartService)
    }

    @Test
    fun malformedIdsReturnInvalidArgumentBody() {
        val expected = "{\"errorCode\":\"INVALID_ARGUMENT\",\"message\":\"Invalid UUID string: not-a-uuid\"}"

        mockMvc.perform(get("/api/shopping-carts/not-a-uuid").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(expected))
        mockMvc.perform(post("/api/shopping-carts/not-a-uuid/pay").with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(expected))
        mockMvc.perform(delete("/api/shopping-carts/{cartId}/items/not-a-uuid", CART).with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(expected))
        verifyNoInteractions(cartService)
    }

    @Test
    fun cartNotFoundIs404WithEnvelope() {
        whenever(cartService.findById(CART)).thenThrow(ShoppingCartNotFoundException("Shopping cart not found"))

        mockMvc.perform(get("/api/shopping-carts/{id}", CART).with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(content().string("{\"errorCode\":\"SHOPPING_CART_NOT_FOUND\",\"message\":\"Shopping cart not found\"}"))
    }

    @Test
    fun createReturns200() {
        whenever(cartService.create(any<CreateShoppingCartDTO>())).thenReturn(cartDto(false))

        mockMvc.perform(
            post("/api/shopping-carts").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER + "\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(CART_JSON))

        verify(cartService).create(CreateShoppingCartDTO(USER))
    }

    @Test
    fun createWithoutUserIdIsValidationError() {
        mockMvc.perform(post("/api/shopping-carts").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"userId: must not be null\"}"))
    }

    @Test
    fun payReturnsPaidCart() {
        whenever(cartService.pay(CART)).thenReturn(cartDto(true))

        mockMvc.perform(post("/api/shopping-carts/{id}/pay", CART).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string(CART_JSON.replace("\"payed\":false", "\"payed\":true")))
    }

    @Test
    fun payTwiceIsConflict() {
        whenever(cartService.pay(CART)).thenThrow(CartAlreadyPaidException("Cannot modify a shopping cart that has already been paid"))

        mockMvc.perform(post("/api/shopping-carts/{id}/pay", CART).with(jwt()))
            .andExpect(status().isConflict())
            .andExpect(content().string("{\"errorCode\":\"CART_ALREADY_PAID\",\"message\":\"Cannot modify a shopping cart that has already been paid\"}"))
    }

    @Test
    fun deleteReturns204() {
        mockMvc.perform(delete("/api/shopping-carts/{id}", CART).with(jwt())).andExpect(status().isNoContent())

        verify(cartService).deleteById(CART)
    }

    @Test
    fun addItemBindsCartIdAndBody() {
        whenever(cartService.addItem(any<UUID>(), any<AddItemCommandDTO>())).thenReturn(itemDto())

        mockMvc.perform(
            post("/api/shopping-carts/{cartId}/items", CART).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + PRODUCT + "\",\"quantity\":2}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(ITEM_JSON))

        verify(cartService).addItem(CART, AddItemCommandDTO(PRODUCT, 2))
    }

    @Test
    fun addItemValidationErrors() {
        val body = mockMvc.perform(
            post("/api/shopping-carts/{cartId}/items", CART).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0}"),
        )
            .andExpect(status().isBadRequest())
            .andReturn().response.contentAsString

        assertThat(body)
            .startsWith("{\"errorCode\":\"VALIDATION_ERROR\"")
            .contains("productId: must not be null")
            .contains("quantity: must be greater than 0")
    }

    @Test
    fun addItemProductNotFoundAndUnavailable() {
        whenever(cartService.addItem(any<UUID>(), any<AddItemCommandDTO>()))
            .thenThrow(ProductNotFoundException("Product not found with id: " + PRODUCT))

        mockMvc.perform(
            post("/api/shopping-carts/{cartId}/items", CART).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + PRODUCT + "\",\"quantity\":1}"),
        )
            .andExpect(status().isNotFound())
            .andExpect(content().string("{\"errorCode\":\"PRODUCT_NOT_FOUND\",\"message\":\"Product not found with id: " + PRODUCT + "\"}"))

        whenever(cartService.addItem(any<UUID>(), any<AddItemCommandDTO>()))
            .thenThrow(ProductUnavailableException("product-service is unavailable: boom"))

        mockMvc.perform(
            post("/api/shopping-carts/{cartId}/items", CART).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + PRODUCT + "\",\"quantity\":1}"),
        )
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string("{\"errorCode\":\"PRODUCT_UNAVAILABLE\",\"message\":\"product-service is unavailable: boom\"}"))
    }

    @Test
    fun updateItemQuantityBindsBothIds() {
        whenever(cartService.updateItemQuantity(any<UUID>(), any<UUID>(), any<UpdateItemQuantityCommandDTO>())).thenReturn(itemDto())

        mockMvc.perform(
            patch("/api/shopping-carts/{cartId}/items/{itemId}", CART, ITEM).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(ITEM_JSON))

        verify(cartService).updateItemQuantity(CART, ITEM, UpdateItemQuantityCommandDTO(2))
    }

    @Test
    fun updateItemQuantityIllegalArgumentIsBadRequest() {
        whenever(cartService.updateItemQuantity(any<UUID>(), any<UUID>(), any<UpdateItemQuantityCommandDTO>()))
            .thenThrow(IllegalArgumentException("Quantity must be greater than zero, but was 0"))

        mockMvc.perform(
            patch("/api/shopping-carts/{cartId}/items/{itemId}", CART, ITEM).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"INVALID_ARGUMENT\",\"message\":\"Quantity must be greater than zero, but was 0\"}"))
    }

    @Test
    fun removeItemReturns204() {
        mockMvc.perform(delete("/api/shopping-carts/{cartId}/items/{itemId}", CART, ITEM).with(jwt()))
            .andExpect(status().isNoContent())

        verify(cartService).removeItem(CART, ITEM)
    }

    companion object {
        private val CART: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000001")
        private val USER: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val PRODUCT: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001")
        private val ITEM: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001")

        private const val ITEM_JSON = "{\"id\":\"b0000000-0000-0000-0000-000000000001\"," +
            "\"productId\":\"a0000000-0000-0000-0000-000000000001\"," +
            "\"shoppingCartId\":\"c0000000-0000-0000-0000-000000000001\",\"quantity\":2,\"unitPrice\":10.50}"
        private const val CART_JSON = "{\"id\":\"c0000000-0000-0000-0000-000000000001\",\"total\":21.00,\"payed\":false," +
            "\"userId\":\"22222222-2222-2222-2222-222222222222\",\"items\":[" + ITEM_JSON + "]}"
    }
}
