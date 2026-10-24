package com.example.ecommerce.cart.infrastructure.config

import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares cart-service's OWN consumer queues + bindings to the shared
 * `ecommerce.events` topic exchange (declared once in buildingblocks'
 * `RabbitMqTopologyConfig`). Per design's explicit topology table:
 * buildingblocks only owns the exchange, each consuming service declares its
 * own queue/binding alongside its `@RabbitListener` (see WU2 apply
 * notes) - this is that declaration for cart-service's two consumers.
 */
@Configuration
class RabbitMqConsumerConfig {

    @Bean
    fun productPriceChangedQueue(): Queue = Queue(PRODUCT_PRICE_CHANGED_QUEUE, true)

    @Bean
    fun productPriceChangedBinding(productPriceChangedQueue: Queue, ecommerceEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(productPriceChangedQueue)
            .to(ecommerceEventsExchange)
            .with(EventRoutingKeys.PRODUCT_PRICE_CHANGED)

    @Bean
    fun userDeletedQueue(): Queue = Queue(USER_DELETED_QUEUE, true)

    @Bean
    fun userDeletedBinding(userDeletedQueue: Queue, ecommerceEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(userDeletedQueue)
            .to(ecommerceEventsExchange)
            .with(EventRoutingKeys.USER_DELETED)

    companion object {
        const val PRODUCT_PRICE_CHANGED_QUEUE = "cart-service.product-price-changed.q"
        const val USER_DELETED_QUEUE = "cart-service.user-deleted.q"
    }
}
