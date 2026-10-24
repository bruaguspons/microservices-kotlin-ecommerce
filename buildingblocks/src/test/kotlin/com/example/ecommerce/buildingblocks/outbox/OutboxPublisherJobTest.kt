package com.example.ecommerce.buildingblocks.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.util.UUID

class OutboxPublisherJobTest {

    private val repository: OutboxMessageRepository = mock()
    private val rabbitTemplate: RabbitTemplate = mock()
    private val job = OutboxPublisherJob(repository, rabbitTemplate)

    @Test
    fun sendsEachPendingRowToTheEventsExchangeWithRoutingKeyAndCorrelationId() {
        val id = UUID.randomUUID()
        val message = OutboxMessageEntity()
        message.id = id
        message.eventType = "user.deleted"
        message.payload = "{\"a\":1}"
        message.status = OutboxStatus.PENDING
        whenever(repository.findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, 5))
            .thenReturn(listOf(message))

        job.publishPending()

        val correlation = argumentCaptor<CorrelationData>()
        verify(rabbitTemplate).convertAndSend(eq("ecommerce.events"), eq("user.deleted"), eq("{\"a\":1}"), correlation.capture())
        assertThat(correlation.firstValue.id).isEqualTo(id.toString())
    }

    @Test
    fun jobDoesNotChangeRowStateItself() {
        val message = OutboxMessageEntity()
        message.id = UUID.randomUUID()
        message.eventType = "cart.paid"
        message.payload = "{}"
        message.status = OutboxStatus.PENDING
        whenever(repository.findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, 5))
            .thenReturn(listOf(message))

        job.publishPending()

        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(message.publishedAt).isNull()
        verify(repository, never()).save(any<OutboxMessageEntity>())
    }

    @Test
    fun nothingPendingSendsNothing() {
        whenever(repository.findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, 5))
            .thenReturn(listOf())

        job.publishPending()

        verify(rabbitTemplate, never()).convertAndSend(any<String>(), any<String>(), any<Any>(), any<CorrelationData>())
    }
}
