package com.example.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * product-service entry point. Placed at the shared `com.example.ecommerce`
 * root package (not `com.example.ecommerce.product`) so Spring Boot's
 * default component/entity scan also covers `com.example.ecommerce.buildingblocks.*`
 * (JWT decoder, outbox infra, event contracts) without extra scan config -
 * see WU2 apply notes.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
class ProductServiceApplication

fun main(args: Array<String>) {
    runApplication<ProductServiceApplication>(*args)
}
