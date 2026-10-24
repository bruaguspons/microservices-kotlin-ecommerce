package com.example.ecommerce.product.infrastructure.config

import com.example.ecommerce.product.application.dto.command.CreateProductCommandDTO
import com.example.ecommerce.product.application.port.`in`.ProductServicePort
import com.example.ecommerce.product.domain.enums.ProductCategory
import net.datafaker.Faker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Seeds fake products on startup so the service is immediately usable for manual/API testing.
 * `create` is `@PreAuthorize("hasRole('ADMIN')")`, so this runs under a synthetic
 * bootstrap Authentication - no real ADMIN account exists in this service yet at boot time.
 */
@Component
@Profile("dev")
class DataSeeder(
    private val productService: ProductServicePort,
) : CommandLineRunner {

    private val faker = Faker()

    override fun run(vararg args: String) {
        runAsSystemAdmin { seedProducts(PRODUCT_COUNT) }
        log.info("DataSeeder: seeded {} fake products (dev profile).", PRODUCT_COUNT)
    }

    private fun seedProducts(count: Int) {
        for (i in 0 until count) {
            val category: ProductCategory = faker.options().option(ProductCategory::class.java)
            val model: String = if (category == ProductCategory.PHONE) {
                faker.options().nextElement(PHONE_MODELS)
            } else {
                faker.options().nextElement(COMPUTER_MODELS)
            }
            val name = model + " " + faker.app().version()

            val price = BigDecimal.valueOf(faker.number().randomDouble(2, 199, 2999))
                .setScale(2, RoundingMode.HALF_UP)

            productService.create(
                CreateProductCommandDTO(
                    name,
                    faker.lorem().sentence(8),
                    price,
                    "/images/" + faker.internet().slug() + ".jpg",
                    category,
                ),
            )
        }
    }

    /**
     * Installs a synthetic ROLE_ADMIN [Authentication] so bootstrap-only calls can pass
     * the `@PreAuthorize("hasRole('ADMIN')")` gate. Cleared in a `finally` block so
     * it never leaks into request-handling threads.
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

        private const val PRODUCT_COUNT = 15
        private val PHONE_MODELS = listOf("iPhone", "Galaxy", "Pixel", "OnePlus", "Xiaomi")
        private val COMPUTER_MODELS = listOf("MacBook", "ThinkPad", "XPS", "ZenBook", "Surface")
    }
}
