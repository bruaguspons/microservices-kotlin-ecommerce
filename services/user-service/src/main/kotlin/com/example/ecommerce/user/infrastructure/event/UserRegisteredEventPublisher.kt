package com.example.ecommerce.user.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.user.UserRegistered
import com.example.ecommerce.buildingblocks.event.EventDispatcher
import com.example.ecommerce.user.domain.model.User
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publishes [UserRegistered] via the shared transactional outbox
 * ([EventDispatcher]) whenever a new user is created. MUST be called
 * from within the same `@Transactional` boundary as the user save so
 * the outbox row commits atomically with the registration (see design:
 * Async Communication / Payment Flow pattern, applied here to user creation).
 */
@Component
class UserRegisteredEventPublisher(
    private val eventDispatcher: EventDispatcher,
) {

    fun publish(user: User) {
        eventDispatcher.publish(
            UserRegistered(
                user.id!!.value,
                user.email!!.value,
                Instant.now(),
            ),
        )
    }
}
