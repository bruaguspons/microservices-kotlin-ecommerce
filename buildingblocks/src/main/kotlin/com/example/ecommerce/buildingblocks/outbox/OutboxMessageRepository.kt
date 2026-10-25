package com.example.ecommerce.buildingblocks.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.Instant
import java.util.UUID

interface OutboxMessageRepository : JpaRepository<OutboxMessageEntity, UUID> {

    /**
     * Backs the publisher poll: PENDING rows under the attempt budget whose
     * backoff/lease has elapsed (NULL = eligible now), oldest first. Must run
     * inside the claim transaction. The row lock plus the lease written by the
     * caller keep concurrent pollers apart; lock.timeout=-2 asks for SKIP LOCKED
     * where the dialect supports it (best effort, otherwise plain FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        "select m from OutboxMessageEntity m where m.status = :status and m.retryCount < :maxAttempts " +
            "and (m.nextAttemptAt is null or m.nextAttemptAt <= :now) order by m.createdAt asc",
    )
    fun findClaimable(status: OutboxStatus, maxAttempts: Int, now: Instant, limit: Limit): List<OutboxMessageEntity>
}
