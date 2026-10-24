package com.example.ecommerce.user.infrastructure.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("dev")
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            Components().addSecuritySchemes(
                SECURITY_SCHEME_NAME,
                SecurityScheme()
                    .name(SECURITY_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"),
            ),
        )

    companion object {
        private const val SECURITY_SCHEME_NAME = "bearerAuth"
    }
}
