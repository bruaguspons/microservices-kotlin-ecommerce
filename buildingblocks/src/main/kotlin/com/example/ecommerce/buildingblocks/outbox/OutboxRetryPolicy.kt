package com.example.ecommerce.buildingblocks.outbox

import java.time.Duration

/**
 * Single source for the outbox delivery budget, shared by the publisher job,
 * the confirm callbacks and the tests.
 */
object OutboxRetryPolicy {
    const val MAX_ATTEMPTS = 8
    const val BATCH_SIZE = 50

    /** How long a claimed row stays invisible to other pollers while awaiting its confirm. */
    val LEASE: Duration = Duration.ofSeconds(60)

    private val BASE_BACKOFF: Duration = Duration.ofSeconds(5)
    private val MAX_BACKOFF: Duration = Duration.ofSeconds(300)

    /** Delay before the next attempt after the given number of failures: min(5s * 2^(n-1), 300s). */
    fun backoffAfter(failures: Int): Duration {
        val exponent = (failures - 1).coerceIn(0, 30)
        val delay = BASE_BACKOFF.multipliedBy(1L shl exponent)
        return if (delay > MAX_BACKOFF) MAX_BACKOFF else delay
    }
}
