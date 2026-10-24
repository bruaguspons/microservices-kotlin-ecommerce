package com.example.ecommerce.product.infrastructure.api

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.dto.command.UpdateProductCommandDTO
import com.example.ecommerce.product.application.dto.result.ProductResultDTO
import com.example.ecommerce.product.application.exception.ProductNotFoundException
import com.example.ecommerce.product.application.port.`in`.ProductServicePort
import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.exception.InvalidPriceException
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.security.SecurityConfig
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(ProductController::class)
@EnableWebSecurity
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class ProductControllerContractTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var productService: ProductServicePort

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var jwtAuthConverter: JwtAuthConverter

    @MockitoBean(name = "jpaMappingContext")
    private lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private fun dto(): ProductResultDTO =
        ProductResultDTO(ID, "iPhone", "desc", BigDecimal("10.50"), "/img.jpg", ProductCategory.PHONE, 2L)

    @Test
    fun getByIdIsPublicAndBindsUuidPathVariableToProductId() {
        whenever(productService.findById(ProductId(ID))).thenReturn(dto())

        mockMvc.perform(get("/api/products/{id}", ID))
            .andExpect(status().isOk())
            .andExpect(content().string(PRODUCT_JSON))

        verify(productService).findById(ProductId(ID))
    }

    @Test
    fun getAllIsPublicAndReturnsArray() {
        whenever(productService.findAll()).thenReturn(listOf(dto()))

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(content().string("[" + PRODUCT_JSON + "]"))
    }

    @Test
    fun malformedIdPathVariableIsBadRequest() {
        mockMvc.perform(get("/api/products/not-a-uuid"))
            .andExpect(status().isBadRequest())

        verifyNoInteractions(productService)
    }

    @Test
    fun malformedIdResponseBodyCurrentBehavior() {
        // IllegalArgumentException raised by ProductId.of(String) is mapped by GlobalExceptionHandler,
        // or Spring's own type mismatch handling answers first. Characterize the exact observed body.
        val body = mockMvc.perform(get("/api/products/not-a-uuid"))
            .andReturn().response.contentAsString
        assertThat(body).isEqualTo(
            "{\"errorCode\":\"INVALID_ARGUMENT\",\"message\":\"Invalid UUID string: not-a-uuid\"}",
        )
    }

    @Test
    fun notFoundReturns404WithIdMessageUsingRecordToString() {
        whenever(productService.findById(ProductId(ID)))
            .thenThrow(ProductNotFoundException("Product not found with id: " + ProductId(ID)))

        mockMvc.perform(get("/api/products/{id}", ID))
            .andExpect(status().isNotFound())
            .andExpect(
                content().string(
                    "{\"errorCode\":\"PRODUCT_NOT_FOUND\","
                        + "\"message\":\"Product not found with id: ProductId[value=11111111-1111-1111-1111-111111111111]\"}",
                ),
            )
    }

    @Test
    fun createWithoutTokenIsUnauthorized() {
        mockMvc.perform(
            post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"price\":1,\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isUnauthorized())

        verifyNoInteractions(productService)
    }

    @Test
    fun createReturns200WithBody() {
        whenever(productService.create(any<CreateProductCommandDTO>())).thenReturn(dto())

        mockMvc.perform(
            post("/api/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"iPhone\",\"desc\":\"desc\",\"price\":10.50,\"imagePath\":\"/img.jpg\",\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(PRODUCT_JSON))

        verify(productService).create(CreateProductCommandDTO("iPhone", "desc", BigDecimal("10.50"), "/img.jpg", ProductCategory.PHONE))
    }

    @Test
    fun createInvalidBodyReturnsValidationErrorJoinedWithSemicolon() {
        val body = mockMvc.perform(
            post("/api/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"),
        )
            .andExpect(status().isBadRequest())
            .andReturn().response.contentAsString

        // Field order of Bean Validation is not guaranteed; assert code and each part.
        assertThat(body).startsWith("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"")
        assertThat(body)
            .contains("name: must not be blank")
            .contains("price: must not be null")
            .contains("category: must not be null")
        assertThat(body.split("; ")).hasSize(3)
        verifyNoInteractions(productService)
    }

    @Test
    fun createNegativePriceFailsBeanValidation() {
        mockMvc.perform(
            post("/api/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"price\":-1,\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"price: must be greater than 0\"}"))
    }

    @Test
    fun invalidPriceDomainExceptionIsBadRequest() {
        whenever(productService.create(any<CreateProductCommandDTO>()))
            .thenThrow(InvalidPriceException("Price must be greater than zero, but was 0"))

        mockMvc.perform(
            post("/api/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"price\":1,\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"INVALID_PRICE\",\"message\":\"Price must be greater than zero, but was 0\"}"))
    }

    @Test
    fun updateBindsPathIdAndBody() {
        whenever(productService.update(any<ProductId>(), any<UpdateProductCommandDTO>())).thenReturn(dto())

        mockMvc.perform(
            put("/api/products/{id}", ID).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"iPhone\",\"desc\":\"desc\",\"price\":10.50,\"imagePath\":\"/img.jpg\",\"category\":\"PHONE\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(PRODUCT_JSON))

        verify(productService).update(
            ProductId(ID),
            UpdateProductCommandDTO("iPhone", "desc", BigDecimal("10.50"), "/img.jpg", ProductCategory.PHONE),
        )
    }

    @Test
    fun updateWithoutTokenIsUnauthorized() {
        mockMvc.perform(put("/api/products/{id}", ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun deleteReturns204() {
        mockMvc.perform(delete("/api/products/{id}", ID).with(jwt()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))

        verify(productService).deleteById(ProductId(ID))
    }

    @Test
    fun deleteWithoutTokenIsUnauthorized() {
        mockMvc.perform(delete("/api/products/{id}", ID))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun unknownEnumValueInBodyIsBadRequest() {
        mockMvc.perform(
            post("/api/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"price\":1,\"category\":\"TOASTER\"}"),
        )
            .andExpect(status().isBadRequest())
    }

    companion object {
        private val ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private const val PRODUCT_JSON =
            "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"name\":\"iPhone\",\"desc\":\"desc\"," +
                "\"price\":10.50,\"imagePath\":\"/img.jpg\",\"category\":\"PHONE\",\"version\":2}"
    }
}
