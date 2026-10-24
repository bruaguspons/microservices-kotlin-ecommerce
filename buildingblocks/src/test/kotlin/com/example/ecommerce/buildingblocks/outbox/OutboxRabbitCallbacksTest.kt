package com.example.ecommerce.buildingblocks.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OutboxRabbitCallbacksTest {

    private val rabbitTemplate: RabbitTemplate = mock()
    private val repository: OutboxMessageRepository = mock()
    private val callbacks = OutboxRabbitCallbacks(rabbitTemplate, repository)
    private val id = UUID.randomUUID()
    private lateinit var message: OutboxMessageEntity

    @BeforeEach
    fun setUp() {
        message = OutboxMessageEntity()
        message.id = id
        message.eventType = "cart.paid"
        message.payload = "{}"
        message.status = OutboxStatus.PENDING
        message.retryCount = 0
        message.createdAt = Instant.now()
        whenever(repository.findById(id)).thenReturn(Optional.of(message))
    }

    @Test
    fun wireCallbacksRegistersItselfOnTheTemplate() {
        callbacks.wireCallbacks()

        verify(rabbitTemplate).setConfirmCallback(callbacks)
        verify(rabbitTemplate).setReturnsCallback(callbacks)
    }

    @Test
    fun ackMarksPublishedWithTimestamp() {
        val before = Instant.now()

        callbacks.confirm(CorrelationData(id.toString()), true, null)

        assertThat(message.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(message.publishedAt).isBetween(before, Instant.now())
        assertThat(message.retryCount).isZero()
        verify(repository).save(message)
    }

    @Test
    fun nackIncrementsRetryCountAndStaysPending() {
        callbacks.confirm(CorrelationData(id.toString()), false, "broker down")

        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(message.retryCount).isEqualTo(1)
        assertThat(message.publishedAt).isNull()
        verify(repository).save(message)
    }

    @Test
    fun fifthNackMovesToDeadLetter() {
        message.retryCount = 4

        callbacks.confirm(CorrelationData(id.toString()), false, "broker down")

        assertThat(message.retryCount).isEqualTo(5)
        assertThat(message.status).isEqualTo(OutboxStatus.DEAD_LETTER)
    }

    @Test
    fun confirmWithoutCorrelationDataIsIgnored() {
        callbacks.confirm(null, true, null)

        verify(repository, never()).findById(any<UUID>())
        verify(repository, never()).save(any<OutboxMessageEntity>())
    }

    @Test
    fun unknownMessageIdIsIgnored() {
        val other = UUID.randomUUID()
        whenever(repository.findById(other)).thenReturn(Optional.empty())

        callbacks.confirm(CorrelationData(other.toString()), true, null)

        verify(repository, never()).save(any<OutboxMessageEntity>())
    }

    @Test
    fun returnedMessageRegistersFailure() {
        val props = MessageProperties()
        props.correlationId = id.toString()
        val returned = ReturnedMessage(Message(ByteArray(0), props), 312, "NO_ROUTE", "ecommerce.events", "cart.paid")

        callbacks.returnedMessage(returned)

        assertThat(message.retryCount).isEqualTo(1)
        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
    }

    @Test
    fun returnedMessageWithoutCorrelationIdIsIgnored() {
        val returned = ReturnedMessage(Message(ByteArray(0), MessageProperties()), 312, "NO_ROUTE", "ecommerce.events", "cart.paid")

        callbacks.returnedMessage(returned)

        verify(repository, never()).save(any<OutboxMessageEntity>())
    }
}
