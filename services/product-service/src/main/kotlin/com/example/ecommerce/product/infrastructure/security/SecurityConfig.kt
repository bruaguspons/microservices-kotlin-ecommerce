package com.example.ecommerce.product.infrastructure.security

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * product-service's own Spring Security resource-server config. Independently
 * validates every incoming JWT's signature/expiry/claims via buildingblocks'
 * `JwtDecoder` (JWKS-backed) - defense-in-depth per design: the gateway
 * only checks for the presence of an Authorization header, this service is
 * the authority on token validity.
 *
 * Coarse access: product reads (GET) are public. Writes require an
 * authenticated principal; the ADMIN role check itself is enforced at the
 * method level via `@PreAuthorize` on
 * [com.example.ecommerce.product.application.service.ProductServiceImpl].
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthConverter: JwtAuthConverter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers -> headers.frameOptions { frameOptions -> frameOptions.sameOrigin() } }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter) }
            }
        return http.build()
    }
}
