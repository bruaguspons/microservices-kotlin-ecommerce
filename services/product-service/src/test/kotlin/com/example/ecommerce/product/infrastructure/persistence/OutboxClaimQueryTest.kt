package com.example.ecommerce.product.infrastructure.persistence

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.ecommerce.buildingblocks.outbox.OutboxMessageEntity
import com.example.ecommerce.buildingblocks.outbox.OutboxMessageRepository
import com.example.ecommerce.buildingblocks.outbox.OutboxRetryPolicy
import com.example.ecommerce.buildingblocks.outbox.OutboxStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.Limit
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
class OutboxClaimQueryTest {

    @Autowired
    private lateinit var repository: OutboxMessageRepository

    private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val sqlAppender = ListAppender<ILoggingEvent>()
    private val sqlLogger = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
    private var previousLevel: Level? = null

    @BeforeEach
    fun captureSql() {
        previousLevel = sqlLogger.level
        sqlLogger.level = Level.DEBUG
        sqlAppender.start()
        sqlLogger.addAppender(sqlAppender)
    }

    @AfterEach
    fun releaseSql() {
        sqlLogger.detachAppender(sqlAppender)
        sqlLogger.level = previousLevel
    }

    private fun row(
        status: OutboxStatus = OutboxStatus.PENDING,
        retryCount: Int = 0,
        nextAttemptAt: Instant? = null,
        createdAt: Instant = now,
    ): OutboxMessageEntity {
        val entity = OutboxMessageEntity()
        entity.eventType = "user.registered"
        entity.payload = "{}"
        entity.status = status
        entity.retryCount = retryCount
        entity.nextAttemptAt = nextAttemptAt
        entity.createdAt = createdAt
        return repository.saveAndFlush(entity)
    }

    private fun claim(limit: Int = OutboxRetryPolicy.BATCH_SIZE) =
        repository.findClaimable(OutboxStatus.PENDING, OutboxRetryPolicy.MAX_ATTEMPTS, now, Limit.of(limit))

    @Test
    fun includesRowsWithNullOrElapsedNextAttemptAtOldestFirst() {
        val elapsed = row(nextAttemptAt = now.minusSeconds(1), createdAt = now.minusSeconds(10))
        val legacy = row(nextAttemptAt = null, createdAt = now.minusSeconds(20))

        assertThat(claim().map { it.id }).containsExactly(legacy.id, elapsed.id)
    }

    @Test
    fun excludesLeasedMaxAttemptAndPublishedRows() {
        val eligible = row()
        row(nextAttemptAt = now.plusSeconds(30))
        row(retryCount = OutboxRetryPolicy.MAX_ATTEMPTS)
        row(status = OutboxStatus.PUBLISHED)
        row(status = OutboxStatus.DEAD_LETTER)

        assertThat(claim().map { it.id }).containsExactly(eligible.id)
    }

    @Test
    fun expiredLeaseMakesTheRowEligibleAgain() {
        val lost = row(nextAttemptAt = now.minus(OutboxRetryPolicy.LEASE).minusSeconds(1))

        assertThat(claim().map { it.id }).containsExactly(lost.id)
    }

    @Test
    fun honoursTheBatchLimit() {
        repeat(3) { row() }

        assertThat(claim(limit = 2)).hasSize(2)
    }

    @Test
    fun runsWithAPessimisticRowLock() {
        row()

        claim()

        val select = sqlAppender.list.map { it.formattedMessage.lowercase() }.first { it.startsWith("select") }
        // H2 either honours SKIP LOCKED or falls back to plain FOR UPDATE; both are valid locks.
        assertThat(select).contains("for update")
    }
}
