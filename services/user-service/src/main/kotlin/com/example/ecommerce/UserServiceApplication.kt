package com.example.ecommerce

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * user-service entry point. Placed at the shared `com.example.ecommerce`
 * root package (not `com.example.ecommerce.user`) so Spring Boot's
 * default component/entity scan also covers `com.example.ecommerce.buildingblocks.*`
 * (JWT decoder, outbox infra, event contracts) without extra scan config -
 * same pattern as product-service (see WU3 apply notes). `@EnableScheduling`
 * is required so buildingblocks' `OutboxPublisherJob` poller fires.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
class UserServiceApplication

fun main(args: Array<String>) {
    runApplication<UserServiceApplication>(*args)
}
