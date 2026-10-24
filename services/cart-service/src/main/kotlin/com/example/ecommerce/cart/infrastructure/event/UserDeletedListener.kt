package com.example.ecommerce.cart.infrastructure.event

import com.example.ecommerce.buildingblocks.contracts.user.UserDeleted
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.infrastructure.config.RabbitMqConsumerConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Consumes `user.deleted` from
 * [RabbitMqConsumerConfig.USER_DELETED_QUEUE]. Minimal (TODO-level,
 * per design) handling - delegates to
 * [ShoppingCartServicePort.handleUserDeleted], which currently only
 * logs. A full archive/soft-delete feature is explicitly NOT required for
 * initial scope.
 */
@Component
class UserDeletedListener(
    private val shoppingCartService: ShoppingCartServicePort,
    private val objectMapper: ObjectMapper,
) {

    @RabbitListener(queues = [RabbitMqConsumerConfig.USER_DELETED_QUEUE])
    fun onMessage(payload: String) {
        try {
            val event = objectMapper.readValue(payload, UserDeleted::class.java)
            shoppingCartService.handleUserDeleted(event.userId)
        } catch (ex: Exception) {
            log.error("Failed to process user.deleted event, skipping. Payload: {}", payload, ex)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(UserDeletedListener::class.java)
    }
}
