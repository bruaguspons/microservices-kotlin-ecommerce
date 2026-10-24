package com.example.ecommerce.cart.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.product.ProductPriceChanged
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.infrastructure.config.RabbitMqConsumerConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Consumes `product.price-changed` from
 * [RabbitMqConsumerConfig.PRODUCT_PRICE_CHANGED_QUEUE]. Contains NO
 * business logic itself - only deserialization + delegation to
 * [ShoppingCartServicePort.handleProductPriceChanged], which owns the
 * optimistic-lock retry loop (see design: Product Price-Change Propagation).
 *
 * Any exception OTHER than what the application layer already handles
 * internally (deserialization failure, unexpected error) is caught here,
 * logged at ERROR with the raw payload, and the message is acknowledged
 * (skipped) - never crashes the listener thread, never blocks the queue.
 */
@Component
class ProductPriceChangedListener(
    private val shoppingCartService: ShoppingCartServicePort,
    private val objectMapper: ObjectMapper,
) {

    @RabbitListener(queues = [RabbitMqConsumerConfig.PRODUCT_PRICE_CHANGED_QUEUE])
    fun onMessage(payload: String) {
        try {
            val event = objectMapper.readValue(payload, ProductPriceChanged::class.java)
            shoppingCartService.handleProductPriceChanged(event.productId, event.newPrice, event.version)
        } catch (ex: Exception) {
            log.error("Failed to process product.price-changed event, skipping. Payload: {}", payload, ex)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ProductPriceChangedListener::class.java)
    }
}
