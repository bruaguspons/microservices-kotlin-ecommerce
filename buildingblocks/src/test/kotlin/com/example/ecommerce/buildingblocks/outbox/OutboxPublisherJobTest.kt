package com.example.ecommerce.buildingblocks.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.Limit
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import java.io.IOException
import java.time.Instant
import java.util.UUID

class OutboxPublisherJobTest {

    private val repository: OutboxMessageRepository = mock()
    private val rabbitTemplate: RabbitTemplate = mock()
    private val callbacks: OutboxRabbitCallbacks = mock()
    private val transactionManager: PlatformTransactionManager = mock()
    private val job = OutboxPublisherJob(repository, rabbitTemplate, callbacks, transactionManager)

    @BeforeEach
    fun setUp() {
        whenever(transactionManager.getTransaction(any<TransactionDefinition>())).thenReturn(SimpleTransactionStatus())
        whenever(repository.findClaimable(eq(OutboxStatus.PENDING), eq(OutboxRetryPolicy.MAX_ATTEMPTS), any(), eq(Limit.of(OutboxRetryPolicy.BATCH_SIZE))))
            .thenReturn(listOf())
    }

    private fun pending(eventType: String = "user.deleted", payload: String = "{}"): OutboxMessageEntity {
        val message = OutboxMessageEntity()
        message.id = UUID.randomUUID()
        message.eventType = eventType
        message.payload = payload
        message.status = OutboxStatus.PENDING
        return message
    }

    private fun claims(vararg messages: OutboxMessageEntity) {
        whenever(repository.findClaimable(eq(OutboxStatus.PENDING), eq(OutboxRetryPolicy.MAX_ATTEMPTS), any(), eq(Limit.of(OutboxRetryPolicy.BATCH_SIZE))))
            .thenReturn(messages.toList())
    }

    @Test
    fun sendsEachClaimedRowToTheEventsExchangeWithRoutingKeyAndCorrelationId() {
        val message = pending(payload = "{\"a\":1}")
        claims(message)

        job.publishPending()

        val correlation = argumentCaptor<CorrelationData>()
        verify(rabbitTemplate).convertAndSend(eq("ecommerce.events"), eq("user.deleted"), eq("{\"a\":1}"), correlation.capture())
        assertThat(correlation.firstValue.id).isEqualTo(message.id.toString())
    }

    @Test
    fun claimIsCommittedWithALeaseBeforeAnythingIsSent() {
        val message = pending()
        claims(message)
        val before = Instant.now()

        job.publishPending()

        assertThat(message.nextAttemptAt).isBetween(before.plus(OutboxRetryPolicy.LEASE), Instant.now().plus(OutboxRetryPolicy.LEASE))
        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(message.publishedAt).isNull()
        val order = inOrder(transactionManager, rabbitTemplate)
        order.verify(transactionManager).commit(any())
        order.verify(rabbitTemplate).convertAndSend(any<String>(), any<String>(), any<Any>(), any<CorrelationData>())
    }

    @Test
    fun sendFailureIsRecordedForThatRowAndTheBatchContinues() {
        val first = pending("a.one")
        val second = pending("a.two")
        val third = pending("a.three")
        claims(first, second, third)
        doThrow(AmqpConnectException(IOException("broker down")))
            .whenever(rabbitTemplate).convertAndSend(any<String>(), eq("a.two"), any<Any>(), any<CorrelationData>())

        job.publishPending()

        verify(rabbitTemplate, times(3)).convertAndSend(any<String>(), any<String>(), any<Any>(), any<CorrelationData>())
        verify(callbacks).recordFailure(eq(second.id!!), any())
        verify(callbacks, never()).recordFailure(eq(first.id!!), any())
        verify(callbacks, never()).recordFailure(eq(third.id!!), any())
    }

    @Test
    fun nothingClaimedSendsNothing() {
        job.publishPending()

        verify(rabbitTemplate, never()).convertAndSend(any<String>(), any<String>(), any<Any>(), any<CorrelationData>())
        verify(callbacks, never()).recordFailure(any(), any())
    }
}
