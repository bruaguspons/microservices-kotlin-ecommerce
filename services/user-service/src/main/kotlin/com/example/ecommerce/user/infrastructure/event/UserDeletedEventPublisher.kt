package com.example.ecommerce.user.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.user.UserDeleted
import com.example.ecommerce.buildingblocks.event.EventDispatcher
import com.example.ecommerce.user.domain.valueobject.UserId
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publishes [UserDeleted] via the shared transactional outbox
 * ([EventDispatcher]) whenever a user is deleted. MUST be called from
 * within the same `@Transactional` boundary as the deletion so the
 * outbox row commits atomically with the delete.
 */
@Component
class UserDeletedEventPublisher(
    private val eventDispatcher: EventDispatcher,
) {

    fun publish(userId: UserId) {
        eventDispatcher.publish(
            UserDeleted(
                userId.value,
                Instant.now(),
            ),
        )
    }
}
