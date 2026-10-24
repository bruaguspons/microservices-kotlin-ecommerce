package com.example.ecommerce.apigateway.config

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Static path-prefix routing to the three downstream services, per design
 * doc's api-gateway routes table. No service discovery - fixed Compose DNS
 * names / localhost ports resolved via [GatewayServiceProperties].
 */
@Configuration
class RouteConfig {

    @Bean
    fun routeLocator(builder: RouteLocatorBuilder, properties: GatewayServiceProperties): RouteLocator =
        builder.routes()
            .route("product-service") { r -> r.path("/api/products/**").uri(properties.productServiceBaseUrl!!) }
            .route("cart-service") { r -> r.path("/api/shopping-carts/**").uri(properties.cartServiceBaseUrl!!) }
            .route("user-service-users") { r -> r.path("/api/users/**").uri(properties.userServiceBaseUrl!!) }
            .route("user-service-admin") { r -> r.path("/api/admin/users/**").uri(properties.userServiceBaseUrl!!) }
            .route("user-service-auth") { r -> r.path("/api/auth/**").uri(properties.userServiceBaseUrl!!) }
            .route("user-service-jwks") { r -> r.path("/.well-known/jwks.json").uri(properties.userServiceBaseUrl!!) }
            .build()
}
