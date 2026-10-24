package com.example.ecommerce.apigateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds `gateway.services.*` - static downstream base URLs (Compose DNS
 * names in prod, localhost ports in dev). No service discovery in scope
 * per design doc.
 */
@ConfigurationProperties(prefix = "gateway.services")
data class GatewayServiceProperties(
    val productServiceBaseUrl: String?,
    val cartServiceBaseUrl: String?,
    val userServiceBaseUrl: String?,
)
