package com.example.ecommerce.product.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.product.ProductPriceChanged
import com.example.ecommerce.buildingblocks.event.EventDispatcher
import com.example.ecommerce.product.domain.model.Product
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publishes [ProductPriceChanged] via the shared transactional outbox
 * ([EventDispatcher]) whenever a product's price changes. MUST be
 * called from within the same `@Transactional` boundary as the product
 * update so the outbox row commits atomically with the price change (see
 * design: Async Communication / Payment Flow pattern, applied here to price
 * updates).
 */
@Component
class ProductPriceChangedEventPublisher(
    private val eventDispatcher: EventDispatcher,
) {

    fun publish(product: Product) {
        eventDispatcher.publish(
            ProductPriceChanged(
                product.id!!.value,
                product.price,
                product.version!!,
                Instant.now(),
            ),
        )
    }
}
