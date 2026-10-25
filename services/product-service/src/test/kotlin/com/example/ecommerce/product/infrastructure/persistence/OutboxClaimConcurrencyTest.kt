package com.example.ecommerce.product.infrastructure.persistence

import com.example.ecommerce.buildingblocks.outbox.OutboxMessageEntity
import com.example.ecommerce.buildingblocks.outbox.OutboxMessageRepository
import com.example.ecommerce.buildingblocks.outbox.OutboxRetryPolicy
import com.example.ecommerce.buildingblocks.outbox.OutboxStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.Limit
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves two concurrent claim transactions never claim the same row. Runs outside the
 * test-managed transaction so each claim commits/locks for real; rows are cleaned up.
 */
@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxClaimConcurrencyTest {

    @Autowired
    private lateinit var repository: OutboxMessageRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @AfterEach
    fun cleanUp() {
        repository.deleteAll()
    }

    private fun seed(count: Int) {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        repeat(count) { i ->
            val entity = OutboxMessageEntity()
            entity.eventType = "user.registered"
            entity.payload = "{}"
            entity.status = OutboxStatus.PENDING
            entity.retryCount = 0
            entity.createdAt = now.minusSeconds((count - i).toLong())
            repository.saveAndFlush(entity)
        }
    }

    @Test
    fun concurrentClaimTransactionsNeverClaimTheSameRow() {
        seed(2)
        val now = Instant.now()
        val aHoldsLock = CountDownLatch(1)
        val bFinished = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val claimA = executor.submit<Set<UUID>> {
                TransactionTemplate(transactionManager).execute {
                    val ids = repository.findClaimable(
                        OutboxStatus.PENDING, OutboxRetryPolicy.MAX_ATTEMPTS, now, Limit.of(1),
                    ).map { it.id!! }.toSet()
                    aHoldsLock.countDown()
                    // keep the row lock until B has attempted its claim
                    check(bFinished.await(10, TimeUnit.SECONDS)) { "tx B did not finish while A held its lock" }
                    ids
                }!!
            }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "tx A never acquired its lock" }
            val claimB = executor.submit<Set<UUID>> {
                try {
                    TransactionTemplate(transactionManager).execute {
                        repository.findClaimable(
                            OutboxStatus.PENDING, OutboxRetryPolicy.MAX_ATTEMPTS, now, Limit.of(2),
                        ).map { it.id!! }.toSet()
                    }!!
                } finally {
                    bFinished.countDown()
                }
            }

            val a = claimA.get(20, TimeUnit.SECONDS)
            val b = claimB.get(20, TimeUnit.SECONDS)

            assertThat(a).`as`("A").hasSize(1)
            // H2 may return an empty set (rather than the other unlocked row) for the skipped-lock
            // claim; the property under test is exclusivity, so B must simply never see A's row.
            assertThat(a.intersect(b)).isEmpty()
        } finally {
            executor.shutdownNow()
        }
    }
}
