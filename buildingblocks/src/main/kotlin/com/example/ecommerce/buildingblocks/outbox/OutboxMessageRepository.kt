package com.example.ecommerce.buildingblocks.outbox

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxMessageRepository : JpaRepository<OutboxMessageEntity, UUID> {

    /**
     * Backs the publisher poll query: `WHERE status='PENDING' AND
     * retry_count < 5 ORDER BY created_at LIMIT 50` (see design: Async
     * Communication / Publisher job).
     */
    fun findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        status: OutboxStatus,
        retryCount: Int,
    ): List<OutboxMessageEntity>
}
