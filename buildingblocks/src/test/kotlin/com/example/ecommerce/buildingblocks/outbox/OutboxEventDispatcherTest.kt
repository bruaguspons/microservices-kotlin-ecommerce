package com.example.ecommerce.buildingblocks.outbox

import com.example.ecommerce.buildingblocks.contracts.cart.CartPaid
import com.example.ecommerce.buildingblocks.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OutboxEventDispatcherTest {

    private val repository: OutboxMessageRepository = mock()
    private val mapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private val dispatcher = OutboxEventDispatcher(repository, mapper)

    @Test
    fun publishSavesPendingRowWithPayload() {
        val cartId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val event = CartPaid(cartId, userId, BigDecimal("10.00"), Instant.parse("2024-01-01T00:00:00Z"))
        val before = Instant.now()

        dispatcher.publish(event)

        val captor = argumentCaptor<OutboxMessageEntity>()
        verify(repository).save(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.eventType).isEqualTo("cart.paid")
        assertThat(saved.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(saved.retryCount).isZero()
        assertThat(saved.publishedAt).isNull()
        assertThat(saved.id).isNull()
        assertThat(saved.createdAt).isBetween(before, Instant.now())
        assertThat(saved.payload).isEqualTo(
            "{\"cartId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"userId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"totalAmount\":10.00,"
                + "\"paidAt\":\"2024-01-01T00:00:00Z\"}",
        )
    }

    @Test
    fun serializationFailureIsWrappedInIllegalStateException() {
        val unserializable = object : DomainEvent {
            override fun eventType(): String = "bad.event"

            @Suppress("unused")
            fun getBoom(): Any = throw RuntimeException("boom")
        }

        assertThatThrownBy { dispatcher.publish(unserializable) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Failed to serialize domain event bad.event")
        verify(repository, never()).save(any<OutboxMessageEntity>())
    }

    @Test
    fun entityDefaultsRetryCountToZeroAndHasNoArgConstructor() {
        val entity = OutboxMessageEntity()
        assertThat(entity.retryCount).isZero()

        val built = OutboxMessageEntity()
        built.eventType = "x"
        assertThat(built.retryCount).isZero()
        assertThat(built.eventType).isEqualTo("x")
    }
}
