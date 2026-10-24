package com.example.ecommerce.cart.infrastructure.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * WebClient beans for cart-service's outbound calls to product-service and
 * user-service (see design: Sync Communication (WebClient)). Each is
 * configured with a 3s connect/read/write timeout; retry and circuit-breaker
 * behavior are applied per-call via Resilience4j annotations on the adapters.
 */
@Configuration
class WebClientConfig {

    @Bean
    fun productServiceWebClient(@Value("\${product-service.base-url}") baseUrl: String): WebClient =
        webClientFor(baseUrl)

    @Bean
    fun userServiceWebClient(@Value("\${user-service.base-url}") baseUrl: String): WebClient =
        webClientFor(baseUrl)

    private fun webClientFor(baseUrl: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_MS)
            .responseTimeout(Duration.ofMillis(TIMEOUT_MS.toLong()))
            .doOnConnected { conn ->
                conn
                    .addHandlerLast(ReadTimeoutHandler(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
            }

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    companion object {
        private const val TIMEOUT_MS = 3000
    }
}
