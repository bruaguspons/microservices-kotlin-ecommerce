package com.example.ecommerce.buildingblocks.outbox

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OutboxRabbitCallbacksTest {

    private val rabbitTemplate: RabbitTemplate = mock()
    private val repository: OutboxMessageRepository = mock()
    private val transactionManager: PlatformTransactionManager = mock()
    private val callbacks = OutboxRabbitCallbacks(rabbitTemplate, repository, transactionManager)
    private val id = UUID.randomUUID()
    private lateinit var message: OutboxMessageEntity
    private val logAppender = ListAppender<ILoggingEvent>()
    private val callbacksLogger = LoggerFactory.getLogger(OutboxRabbitCallbacks::class.java) as Logger

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
        whenever(transactionManager.getTransaction(any<TransactionDefinition>())).thenReturn(SimpleTransactionStatus())
        logAppender.start()
        callbacksLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        callbacksLogger.detachAppender(logAppender)
    }

    private fun returnedCorrelation(): CorrelationData {
        val correlation = CorrelationData(id.toString())
        val props = MessageProperties()
        props.correlationId = id.toString()
        correlation.setReturned(ReturnedMessage(Message(ByteArray(0), props), 312, "NO_ROUTE", "ecommerce.events", "cart.paid"))
        return correlation
    }

    @Test
    fun wireCallbacksRegistersItselfOnTheTemplate() {
        callbacks.wireCallbacks()

        verify(rabbitTemplate).setConfirmCallback(callbacks)
        verify(rabbitTemplate).setReturnsCallback(callbacks)
    }

    @Test
    fun ackMarksPublishedWithTimestampInsideATransaction() {
        val before = Instant.now()

        callbacks.confirm(CorrelationData(id.toString()), true, null)

        assertThat(message.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(message.publishedAt).isBetween(before, Instant.now())
        assertThat(message.retryCount).isZero()
        verify(repository).save(message)
        verify(transactionManager).commit(any())
    }

    @Test
    fun ackOfAReturnedMessageIsAFailureNeverPublished() {
        callbacks.confirm(returnedCorrelation(), true, null)

        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(message.publishedAt).isNull()
        assertThat(message.retryCount).isEqualTo(1)
        assertThat(message.nextAttemptAt).isNotNull()
    }

    @Test
    fun nackIncrementsRetryCountAndSchedulesBackoff() {
        val before = Instant.now()

        callbacks.confirm(CorrelationData(id.toString()), false, "broker down")

        assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(message.retryCount).isEqualTo(1)
        assertThat(message.publishedAt).isNull()
        assertThat(message.nextAttemptAt)
            .isBetween(before.plus(OutboxRetryPolicy.backoffAfter(1)), Instant.now().plus(OutboxRetryPolicy.backoffAfter(1)))
        verify(repository).save(message)
    }

    @Test
    fun backoffGrowsWithEachFailure() {
        message.retryCount = 2
        val before = Instant.now()

        callbacks.recordFailure(id, "boom")

        assertThat(message.retryCount).isEqualTo(3)
        assertThat(message.nextAttemptAt).isAfterOrEqualTo(before.plus(OutboxRetryPolicy.backoffAfter(3)))
    }

    @Test
    fun lastAllowedFailureMovesToDeadLetterAndLogsStructuredError() {
        message.retryCount = OutboxRetryPolicy.MAX_ATTEMPTS - 1

        callbacks.confirm(CorrelationData(id.toString()), false, "broker down")

        assertThat(message.retryCount).isEqualTo(OutboxRetryPolicy.MAX_ATTEMPTS)
        assertThat(message.status).isEqualTo(OutboxStatus.DEAD_LETTER)
        assertThat(message.nextAttemptAt).isNull()
        val errors = logAppender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        val formatted = errors.single().formattedMessage
        assertThat(formatted).contains(id.toString(), "cart.paid", OutboxRetryPolicy.MAX_ATTEMPTS.toString(), "broker down")
    }

    @Test
    fun persistentlyUnroutableMessageDeadLettersAfterMaxAttemptsAndIsNeverPublished() {
        var previousNextAttempt: Instant? = null
        repeat(OutboxRetryPolicy.MAX_ATTEMPTS) { attempt ->
            callbacks.confirm(returnedCorrelation(), true, null)

            assertThat(message.retryCount).isEqualTo(attempt + 1)
            assertThat(message.publishedAt).isNull()
            assertThat(message.status).isNotEqualTo(OutboxStatus.PUBLISHED)
            if (attempt + 1 < OutboxRetryPolicy.MAX_ATTEMPTS) {
                assertThat(message.status).isEqualTo(OutboxStatus.PENDING)
                val next = message.nextAttemptAt
                assertThat(next).isNotNull()
                if (previousNextAttempt != null) {
                    // backoff grows (or is capped), so the schedule never shrinks
                    assertThat(next).isAfterOrEqualTo(previousNextAttempt)
                }
                previousNextAttempt = next
            }
        }

        assertThat(message.status).isEqualTo(OutboxStatus.DEAD_LETTER)
        assertThat(message.nextAttemptAt).isNull()
    }

    @Test
    fun nonPendingRowIgnoresLateCallbacks() {
        message.status = OutboxStatus.PUBLISHED
        message.retryCount = 0

        callbacks.confirm(CorrelationData(id.toString()), false, "late nack")
        callbacks.confirm(CorrelationData(id.toString()), true, null)

        assertThat(message.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(message.retryCount).isZero()
        verify(repository, never()).save(any<OutboxMessageEntity>())
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
    fun returnedMessageOnlyLogsAndDoesNotTouchTheDatabase() {
        val props = MessageProperties()
        props.correlationId = id.toString()
        val returned = ReturnedMessage(Message(ByteArray(0), props), 312, "NO_ROUTE", "ecommerce.events", "cart.paid")

        callbacks.returnedMessage(returned)

        verifyNoInteractions(repository)
        assertThat(message.retryCount).isZero()
        assertThat(logAppender.list.map { it.level }).contains(Level.WARN)
    }
}
