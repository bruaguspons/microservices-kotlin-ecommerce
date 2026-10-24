package com.example.ecommerce.cart.infrastructure.config

import com.example.ecommerce.cart.application.dto.command.AddItemCommandDTO
import com.example.ecommerce.cart.application.dto.command.CreateShoppingCartDTO
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.reactive.function.client.WebClient
import java.lang.reflect.Proxy
import java.util.Random
import java.util.UUID

/**
 * Seeds fake shopping carts on startup so the service is immediately usable for manual/API
 * testing. Unlike product-service/user-service, cart-service owns no data of its own to seed
 * from scratch: it needs real product and user IDs, which only exist once those two services
 * have run their own dev seeders. This seeder therefore calls out over HTTP (reusing the same
 * `productServiceWebClient`/`userServiceWebClient` beans the app uses for real
 * cross-service reads - see [WebClientConfig]) to discover seeded IDs at boot time.
 *
 * This means product-service and user-service must already be up and seeded before
 * cart-service starts (see README's local-dev startup order). If they aren't reachable yet,
 * cart seeding is skipped with a warning - it never fails application startup.
 *
 * `create`/`addItem` are `@PreAuthorize("hasRole('ADMIN') or
 * @ownership...")`, so this runs under a synthetic bootstrap Authentication (ADMIN satisfies
 * both branches uniformly for any seeded user). That covers cart-service's own method security,
 * but `create` also makes an outbound existence check to user-service
 * ([com.example.ecommerce.cart.infrastructure.client.UserWebClientAdapter]), which forwards
 * whatever `Authorization` header is on the *current inbound HTTP request*
 * (`CurrentRequestAuth`) - there is none here, since a [CommandLineRunner] runs
 * outside any request. A fake request is bound to [RequestContextHolder] carrying the
 * seeded admin's real JWT so that downstream call authenticates against user-service too.
 */
@Component
@Profile("dev")
class DataSeeder(
    private val shoppingCartService: ShoppingCartServicePort,
    private val productServiceWebClient: WebClient,
    private val userServiceWebClient: WebClient,
) : CommandLineRunner {

    private val random = Random()

    private data class SeedProduct(val id: UUID?)

    private data class SeedUser(val id: UUID?, val email: String?)

    private data class LoginRequest(val email: String?, val password: String?)

    private data class LoginResponse(val token: String?)

    override fun run(vararg args: String) {
        val productIds = fetchProductIds()
        val adminToken = tryLoginAsAdmin()
        val userIds = if (adminToken == null) emptyList() else fetchUserIds(adminToken)

        if (productIds.isEmpty() || userIds.isEmpty()) {
            log.warn(
                "DataSeeder: skipping cart seeding — could not fetch seeded products/users " +
                    "from product-service/user-service. Make sure both are started (and their " +
                    "own dev seeders have run) before cart-service; see README.",
            )
            return
        }

        withForwardedAuth(adminToken) { runAsSystemAdmin { seedCarts(userIds, productIds) } }
        log.info("DataSeeder: seeded up to {} fake shopping carts (dev profile).", MAX_CARTS)
    }

    private fun fetchProductIds(): List<UUID?> {
        try {
            val products = productServiceWebClient.get()
                .uri("/api/products")
                .retrieve()
                .bodyToFlux(SeedProduct::class.java)
                .collectList()
                .block()
            return if (products == null) emptyList() else products.map { it.id }
        } catch (ex: RuntimeException) {
            log.warn("DataSeeder: could not reach product-service to fetch seed products: {}", ex.message)
            return emptyList()
        }
    }

    private fun fetchUserIds(adminToken: String): List<UUID?> {
        try {
            val users = userServiceWebClient.get()
                .uri("/api/users")
                .headers { headers -> headers.setBearerAuth(adminToken) }
                .retrieve()
                .bodyToFlux(SeedUser::class.java)
                .collectList()
                .block()
            return if (users == null) {
                emptyList()
            } else {
                users
                    .filter { u -> ADMIN_EMAIL != u.email }
                    .map { it.id }
            }
        } catch (ex: RuntimeException) {
            log.warn("DataSeeder: could not reach user-service to fetch seed users: {}", ex.message)
            return emptyList()
        }
    }

    private fun tryLoginAsAdmin(): String? {
        try {
            val response = userServiceWebClient.post()
                .uri("/api/auth/login")
                .bodyValue(LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))
                .retrieve()
                .bodyToMono(LoginResponse::class.java)
                .block()
            if (response == null || response.token == null) {
                throw IllegalStateException("user-service login returned no token")
            }
            return response.token
        } catch (ex: RuntimeException) {
            log.warn("DataSeeder: could not log in as seed admin against user-service: {}", ex.message)
            return null
        }
    }

    private fun seedCarts(userIds: List<UUID?>, productIds: List<UUID?>) {
        val cartsToCreate = Math.min(MAX_CARTS, userIds.size)
        for (i in 0 until cartsToCreate) {
            val userId = userIds[i]
            val cart = shoppingCartService.create(CreateShoppingCartDTO(userId))

            val itemCount = 1 + random.nextInt(MAX_ITEMS_PER_CART)
            for (j in 0 until itemCount) {
                val productId = productIds[random.nextInt(productIds.size)]
                val quantity = 1 + random.nextInt(3)
                shoppingCartService.addItem(cart.id, AddItemCommandDTO(productId, quantity))
            }
        }
    }

    /**
     * Binds a fake [HttpServletRequest] to [RequestContextHolder] whose only real
     * behavior is returning `Bearer <token>` for `getHeader("Authorization")` - the
     * one call `CurrentRequestAuth` makes when cart-service forwards auth downstream to
     * user-service. All other methods return a primitive-safe default since nothing else in this
     * codepath touches the request. Cleared in a `finally` block.
     */
    private fun withForwardedAuth(token: String?, action: Runnable) {
        val fakeRequest = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf<Class<*>>(HttpServletRequest::class.java),
        ) { _, method, methodArgs ->
            if ("getHeader" == method.name && methodArgs != null && methodArgs.size == 1 &&
                HttpHeaders.AUTHORIZATION.equals(methodArgs[0].toString(), ignoreCase = true)
            ) {
                "Bearer $token"
            } else {
                val returnType = method.returnType
                if (returnType == Boolean::class.javaPrimitiveType) {
                    false
                } else if (returnType.isPrimitive) {
                    0
                } else {
                    null
                }
            }
        } as HttpServletRequest

        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(fakeRequest))
        try {
            action.run()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    /**
     * Installs a synthetic ROLE_ADMIN [Authentication] so bootstrap-only calls can pass
     * the ownership-based `@PreAuthorize` gates for any seeded user's cart. Cleared in a
     * `finally` block so it never leaks into request-handling threads.
     */
    private fun runAsSystemAdmin(action: Runnable) {
        val bootstrapAuth: Authentication = UsernamePasswordAuthenticationToken(
            "system-seeder", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )
        SecurityContextHolder.getContext().authentication = bootstrapAuth
        try {
            action.run()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DataSeeder::class.java)

        private const val ADMIN_EMAIL = "admin@ecommerce.dev"
        private const val ADMIN_PASSWORD = "Admin123!"
        private const val MAX_CARTS = 5
        private const val MAX_ITEMS_PER_CART = 3
    }
}
