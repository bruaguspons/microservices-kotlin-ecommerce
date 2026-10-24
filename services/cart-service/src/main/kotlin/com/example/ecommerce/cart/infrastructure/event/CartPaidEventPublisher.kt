package com.example.ecommerce.cart.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.cart.CartPaid
import com.example.ecommerce.buildingblocks.event.EventDispatcher
import com.example.ecommerce.cart.domain.model.ShoppingCart
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publishes [CartPaid] via the shared transactional outbox
 * ([EventDispatcher]) when `ShoppingCart.pay()` succeeds. MUST
 * be called from within the same `@Transactional` boundary as the
 * cart save (see design: Payment Flow).
 */
@Component
class CartPaidEventPublisher(
    private val eventDispatcher: EventDispatcher,
) {

    fun publish(cart: ShoppingCart) {
        eventDispatcher.publish(
            CartPaid(
                cart.id!!.value,
                cart.userId!!.value,
                cart.total,
                Instant.now(),
            ),
        )
    }
}
