package com.example.ecommerce.apigateway.config

import com.example.ecommerce.apigateway.filter.AuthForwardingFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.URI

@SpringBootTest
class GatewayRoutingContextTest {

    @Autowired
    private lateinit var routeLocator: RouteLocator

    @Autowired
    private lateinit var properties: GatewayServiceProperties

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun routeIdsAndTargets() {
        val routes: List<Route> = routeLocator.routes.collectList().block()!!

        val byId: Map<String, URI> = routes.associate { it.id to it.uri }
        assertThat(byId).containsOnlyKeys(
            "product-service", "cart-service", "user-service-users", "user-service-admin",
            "user-service-auth", "user-service-jwks",
        )
        assertThat(byId["product-service"]).isEqualTo(URI.create("http://localhost:8081"))
        assertThat(byId["cart-service"]).isEqualTo(URI.create("http://localhost:8082"))
        assertThat(byId["user-service-users"]).isEqualTo(URI.create("http://localhost:8083"))
        assertThat(byId["user-service-admin"]).isEqualTo(URI.create("http://localhost:8083"))
        assertThat(byId["user-service-auth"]).isEqualTo(URI.create("http://localhost:8083"))
        assertThat(byId["user-service-jwks"]).isEqualTo(URI.create("http://localhost:8083"))
    }

    @Test
    fun routeOrderIsDeclarationOrder() {
        val ids: List<String> = routeLocator.routes.map { it.id }.collectList().block()!!

        assertThat(ids).containsExactly(
            "product-service", "cart-service", "user-service-users", "user-service-admin",
            "user-service-auth", "user-service-jwks",
        )
    }

    @Test
    fun propertiesBindFromGatewayServicesPrefix() {
        assertThat(properties.productServiceBaseUrl).isEqualTo("http://localhost:8081")
        assertThat(properties.cartServiceBaseUrl).isEqualTo("http://localhost:8082")
        assertThat(properties.userServiceBaseUrl).isEqualTo("http://localhost:8083")
    }

    @Test
    fun authFilterIsRegisteredAsGlobalFilterAndRejectsThroughTheFullChain() {
        assertThat(context.getBeansOfType(AuthForwardingFilter::class.java)).hasSize(1)
        val client = WebTestClient.bindToApplicationContext(context).build()

        client.get().uri("/api/shopping-carts")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType("application/json")
            .expectBody(String::class.java)
            .isEqualTo("{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Missing Authorization header\"}")
    }
}
