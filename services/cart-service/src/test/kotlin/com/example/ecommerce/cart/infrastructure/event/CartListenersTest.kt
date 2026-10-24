package com.example.ecommerce.cart.infrastructure.event

import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.infrastructure.config.RabbitMqConsumerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID

class CartListenersTest {

    private val service: ShoppingCartServicePort = mock()
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun priceChangedPayloadIsDeserializedAndDelegated() {
        ProductPriceChangedListener(service, mapper).onMessage(
            "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"newPrice\":12.50,\"version\":7,\"changedAt\":\"2024-05-06T07:08:09Z\"}",
        )

        verify(service).handleProductPriceChanged(ID, BigDecimal("12.50"), 7L)
    }

    @Test
    fun priceChangedMalformedPayloadIsSwallowed() {
        ProductPriceChangedListener(service, mapper).onMessage("not json")

        verifyNoInteractions(service)
    }

    @Test
    fun priceChangedPayloadWithoutVersionIsSwallowedBecausePrimitiveIsRequired() {
        ProductPriceChangedListener(service, mapper).onMessage(
            "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"newPrice\":12.50}",
        )

        verifyNoInteractions(service)
    }

    @Test
    fun serviceFailureIsSwallowed() {
        doThrow(IllegalStateException("db down")).whenever(service)
            .handleProductPriceChanged(any<UUID>(), any<BigDecimal>(), anyLong())

        ProductPriceChangedListener(service, mapper).onMessage(
            "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"newPrice\":1,\"version\":1}",
        )

        verify(service).handleProductPriceChanged(ID, BigDecimal("1"), 1L)
    }

    @Test
    fun userDeletedPayloadIsDeserializedAndDelegated() {
        UserDeletedListener(service, mapper).onMessage(
            "{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"deletedAt\":\"2024-05-06T07:08:09Z\"}",
        )

        verify(service).handleUserDeleted(ID)
    }

    @Test
    fun userDeletedMalformedPayloadIsSwallowed() {
        UserDeletedListener(service, mapper).onMessage("{")

        verifyNoInteractions(service)
    }

    @Test
    fun queueNames() {
        assertThat(RabbitMqConsumerConfig.PRODUCT_PRICE_CHANGED_QUEUE).isEqualTo("cart-service.product-price-changed.q")
        assertThat(RabbitMqConsumerConfig.USER_DELETED_QUEUE).isEqualTo("cart-service.user-deleted.q")
    }

    companion object {
        private val ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
