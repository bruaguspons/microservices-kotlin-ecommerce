package com.example.ecommerce.user.infrastructure.config

import com.example.ecommerce.user.application.dto.command.AdminCreateUserCommandDTO
import com.example.ecommerce.user.application.dto.command.RegisterUserCommandDTO
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import com.example.ecommerce.user.domain.enums.UserRole
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

/**
 * Seeds fake accounts on startup so the service is immediately usable for manual/API testing.
 * `createByAdmin` is `@PreAuthorize("hasRole('ADMIN')")`, so it runs under a synthetic bootstrap
 * Authentication - no real ADMIN account exists in this service yet at boot time. All fake random
 * users share [SEED_PASSWORD] (rather than a random one per user) so they stay usable for manual
 * login testing.
 */
@Component
@Profile("dev")
class DataSeeder(
    private val userService: UserServicePort,
) : CommandLineRunner {

    private val faker = Faker()

    override fun run(vararg args: String) {
        runAsSystemAdmin {
            userService.createByAdmin(
                AdminCreateUserCommandDTO(
                    SEED_ADMIN_EMAIL, "Admin", "Root", 30, SEED_ADMIN_PASSWORD, UserRole.ADMIN,
                ),
            )
        }

        userService.register(
            RegisterUserCommandDTO(
                SEED_USER_EMAIL, "Regular", "User", 25, SEED_PASSWORD,
            ),
        )

        seedUsers(RANDOM_USER_COUNT)

        log.info(
            "DataSeeder: seeded 2 fixed accounts + {} fake users (dev profile). " +
                "Fixed admin: {} / {}. Fixed user: {} / {}. All seeded random users share password {}.",
            RANDOM_USER_COUNT, SEED_ADMIN_EMAIL, SEED_ADMIN_PASSWORD, SEED_USER_EMAIL, SEED_PASSWORD,
            SEED_PASSWORD,
        )
    }

    private fun seedUsers(count: Int) {
        for (i in 0 until count) {
            val firstName: String = faker.name().firstName()
            val lastName: String = faker.name().lastName()
            val email = (firstName + "." + lastName + i + "@" + faker.internet().domainName()).lowercase()

            userService.register(
                RegisterUserCommandDTO(
                    email,
                    firstName,
                    lastName,
                    faker.number().numberBetween(18, 65),
                    SEED_PASSWORD,
                ),
            )
        }
    }

    /**
     * Installs a synthetic ROLE_ADMIN [Authentication] so bootstrap-only calls can pass
     * the `@PreAuthorize("hasRole('ADMIN')")` gate on `createByAdmin` before any
     * real ADMIN account exists. Cleared in a `finally` block so it never leaks into
     * request-handling threads.
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

        const val SEED_ADMIN_EMAIL = "admin@ecommerce.dev"
        const val SEED_ADMIN_PASSWORD = "Admin123!"
        const val SEED_USER_EMAIL = "user@ecommerce.dev"
        const val SEED_PASSWORD = "User123!"
        private const val RANDOM_USER_COUNT = 10
    }
}
