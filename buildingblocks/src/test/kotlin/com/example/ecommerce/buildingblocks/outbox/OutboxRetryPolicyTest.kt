package com.example.ecommerce.buildingblocks.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class OutboxRetryPolicyTest {

    @Test
    fun backoffDoublesFromFiveSecondsAndCapsAtThreeHundred() {
        val seconds = (1..OutboxRetryPolicy.MAX_ATTEMPTS).map { OutboxRetryPolicy.backoffAfter(it).seconds }

        assertThat(seconds).containsExactly(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L)
    }

    @Test
    fun backoffStaysCappedForLargeFailureCounts() {
        assertThat(OutboxRetryPolicy.backoffAfter(100)).isEqualTo(Duration.ofSeconds(300))
    }

    @Test
    fun leaseAndBatchSizeMatchTheSpec() {
        assertThat(OutboxRetryPolicy.LEASE).isEqualTo(Duration.ofSeconds(60))
        assertThat(OutboxRetryPolicy.BATCH_SIZE).isEqualTo(50)
        assertThat(OutboxRetryPolicy.MAX_ATTEMPTS).isEqualTo(8)
    }
}
