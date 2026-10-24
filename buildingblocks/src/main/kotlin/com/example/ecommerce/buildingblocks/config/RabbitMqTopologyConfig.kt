package com.example.ecommerce.buildingblocks.config

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the single shared topic exchange used by every publisher/consumer
 * in the platform (see design: Async Communication / RabbitMQ topology).
 *
 * Only the exchange is declared here. Per-event consumer queues/bindings
 * (e.g. `cart-service.product-price-changed.q`,
 * `cart-service.user-deleted.q`) are declared by the consuming service
 * itself, alongside its `@RabbitListener`, so no queue is bound before
 * a real consumer exists for it (WU5 adds cart-service's listener queues).
 */
@Configuration
class RabbitMqTopologyConfig {

    @Bean
    fun ecommerceEventsExchange(): TopicExchange = TopicExchange(EVENTS_EXCHANGE, true, false)

    companion object {
        const val EVENTS_EXCHANGE = "ecommerce.events"
    }
}
