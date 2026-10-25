package com.example.ecommerce.buildingblocks.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Transactional outbox row, per-service table (each service owns its own
 * `outbox_message` table in its own schema - see design: Async
 * Communication / Outbox table).
 */
@Entity
@Table(name = "outbox_message")
class OutboxMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String? = null,

    @Lob
    @Column(name = "payload", nullable = false)
    var payload: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    // NULL = eligible immediately (rows created before backoff/lease existed).
    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant? = null,
)
