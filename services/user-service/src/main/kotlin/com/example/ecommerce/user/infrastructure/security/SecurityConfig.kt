package com.example.ecommerce.user.infrastructure.security

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

/**
 * user-service's Spring Security configuration. Unlike product-service /
 * cart-service, this service plays BOTH roles (see design: JWT / Auth
 * Design):
 *
 *  - ISSUER - `POST /api/auth/login` authenticates email/password
 *    via the standard [AuthenticationManager]/[DaoAuthenticationProvider]
 *    stack and [JwtService] signs a new RS256 token with the RSA
 *    private key from [JwtKeyProvider].
 *  - VERIFIER - every other protected endpoint (e.g. `/api/users/{id}`,
 *    the `/api/admin/users` subtree) is guarded the same way as
 *    product-service: buildingblocks' JWKS-backed `JwtDecoder` +
 *    [JwtAuthConverter] via `oauth2ResourceServer`. user-service
 *    points its own `jwt.jwks-uri` at itself.
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
                    .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
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

    /**
     * Password encoder bean used to hash user credentials.
     *
     * @return a [BCryptPasswordEncoder] instance
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * Exposes Spring Security's [AuthenticationManager], used by
     * [com.example.ecommerce.user.application.service.AuthServiceImpl]
     * to authenticate login attempts.
     *
     * @param config the authentication configuration
     * @return the configured [AuthenticationManager]
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    /**
     * Wires the email/password authentication provider used during login.
     *
     * @param userDetailsService loads users by email
     * @param encoder verifies the submitted password against the stored hash
     * @return the configured [DaoAuthenticationProvider]
     */
    @Bean
    fun authenticationProvider(
        userDetailsService: SecurityUserDetailsService,
        encoder: PasswordEncoder,
    ): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(encoder)
        return provider
    }
}
