package com.example.ecommerce.apigateway

import com.example.ecommerce.apigateway.config.GatewayServiceProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * api-gateway entry point. Pure routing layer (Spring Cloud Gateway, WebFlux) -
 * no domain layer, no dependency on `buildingblocks` (which is
 * servlet/JPA-oriented and would pull in incompatible infra for a reactive
 * app). See design doc's api-gateway section: routing + Authorization-header
 * presence enforcement only, no JWT decoding here.
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayServiceProperties::class)
class ApiGatewayApplication

fun main(args: Array<String>) {
    runApplication<ApiGatewayApplication>(*args)
}
