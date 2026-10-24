package com.example.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * cart-service entry point. Placed at the shared `com.example.ecommerce`
 * root package (same convention as product-service/user-service) so Spring
 * Boot's default component/entity scan also covers
 * `com.example.ecommerce.buildingblocks.*` without extra scan config.
 * `@EnableScheduling` drives buildingblocks' `OutboxPublisherJob`.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
class CartServiceApplication

fun main(args: Array<String>) {
    runApplication<CartServiceApplication>(*args)
}
