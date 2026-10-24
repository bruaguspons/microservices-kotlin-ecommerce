package com.example.ecommerce.cart.infrastructure.security

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * cart-service's own Spring Security resource-server config. Independently
 * validates every incoming JWT via buildingblocks' JWKS-backed
 * `JwtDecoder` - defense-in-depth per design, same pattern as
 * product-service/user-service. Every route under `/api/shopping-carts`
 * requires authentication; ownership itself is enforced via
 * `@PreAuthorize`/[OwnershipGuard] at the service layer.
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
