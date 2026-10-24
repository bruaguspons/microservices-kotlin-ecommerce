package com.example.ecommerce.user.e2e

import com.example.ecommerce.buildingblocks.outbox.OutboxPublisherJob
import com.example.ecommerce.user.application.port.`in`.AuthServicePort
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/** Full context on H2 with the dev seeder (admin@ecommerce.dev / user@ecommerce.dev). The Rabbit publisher job is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceContextTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userService: UserServicePort

    @Autowired
    private lateinit var authService: AuthServicePort

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var outboxPublisherJob: OutboxPublisherJob

    private fun asRole(role: String, subject: UUID): RequestPostProcessor =
        jwt().jwt { it.subject(subject.toString()) }.authorities(GrantedAuthority { "ROLE_$role" })

    private fun json(body: String): JsonNode = objectMapper.readTree(body)

    private fun login(email: String, password: String): String {
        val body = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"),
        )
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        return json(body).get("token").asString()
    }

    private fun register(email: String): UUID {
        val body = mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"firstname\":\"Ann\",\"lastname\":\"Lee\",\"age\":28,\"password\":\"Passw0rdX\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.role").value("USER"))
            .andReturn().response.contentAsString
        return UUID.fromString(json(body).get("id").asString())
    }

    @Test
    fun servicesAreAopProxies() {
        assertThat(AopUtils.isAopProxy(userService)).isTrue()
        assertThat(AopUtils.isAopProxy(authService)).isFalse()
    }

    @Test
    fun loginIssuesRs256TokenThatVerifiesAgainstJwksWithExpectedClaims() {
        val token = login(ADMIN_EMAIL, ADMIN_PASSWORD)

        val jwks = mockMvc.perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        val key = JWKSet.parse(jwks).keys[0] as RSAKey
        assertThat(key.keyID).isEqualTo("user-service-key-1")

        val jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token)

        assertThat(jwt.headers).containsEntry("alg", "RS256").containsEntry("kid", "user-service-key-1")
        assertThat(jwt.subject).isEqualTo(
            jdbc.queryForObject("select id from users where email = ?", UUID::class.java, ADMIN_EMAIL)!!.toString(),
        )
        assertThat(jwt.getClaimAsString("email")).isEqualTo(ADMIN_EMAIL)
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN")
        assertThat(jwt.expiresAt).isAfter(Instant.now())
        assertThat(jwt.issuedAt).isBefore(jwt.expiresAt)
        assertThat(jwt.expiresAt!!.epochSecond - jwt.issuedAt!!.epochSecond).isEqualTo(3600L)
    }

    @Test
    fun wrongPasswordIs401InvalidCredentials() {
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"nope\"}"),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("\$.errorCode").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("\$.message").value("Invalid email or password"))
    }

    @Test
    fun unknownEmailIs401InvalidCredentials() {
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ghost@example.com\",\"password\":\"x\"}"),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("\$.errorCode").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun registerWritesOutboxRowAndDuplicateIsConflict() {
        val email = "register-outbox@example.com"
        val id = register(email)

        val payloads = jdbc.queryForList(
            "select payload from outbox_message where event_type = 'user.registered' and payload like ?", String::class.java, "%" + id + "%",
        )
        assertThat(payloads).hasSize(1)
        assertThat(payloads[0]).contains("\"userId\":\"" + id + "\"").contains("\"email\":\"" + email + "\"")
        assertThat(jdbc.queryForObject("select status from outbox_message where payload like ?", String::class.java, "%" + id + "%"))
            .isEqualTo("PENDING")

        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"firstname\":\"Ann\",\"lastname\":\"Lee\",\"age\":28,\"password\":\"Passw0rdX\"}"),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("\$.errorCode").value("USER_ALREADY_EXISTS"))
            .andExpect(jsonPath("\$.message").value("User alredy exists"))
    }

    @Test
    fun registerStoresBcryptHashNotPlainPassword() {
        val id = register("hashcheck@example.com")

        val stored = jdbc.queryForObject("select password from users where id = ?", String::class.java, id)

        assertThat(stored).startsWith("\$2").isNotEqualTo("Passw0rdX")
    }

    @Test
    fun registerUnderageAndInvalidEmail() {
        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"kid@example.com\",\"firstname\":\"K\",\"lastname\":\"D\",\"age\":10,\"password\":\"Passw0rdX\"}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("\$.errorCode").value("UNDERAGE_USER"))
            .andExpect(jsonPath("\$.message").value("User must be at least 18 years old, but was 10"))

        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Not An Email\",\"firstname\":\"K\",\"lastname\":\"D\",\"age\":30,\"password\":\"Passw0rdX\"}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("\$.errorCode").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("\$.message").value("Email is not valid"))
    }

    @Test
    fun ownershipRulesAreEnforcedByMethodSecurity() {
        val ann = register("ownership-ann@example.com")
        val bob = register("ownership-bob@example.com")

        mockMvc.perform(get("/api/users/{id}", ann)).andExpect(status().isUnauthorized())
        mockMvc.perform(get("/api/users/{id}", ann).with(asRole("USER", ann)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.email").value("ownership-ann@example.com"))
            .andExpect(jsonPath("\$.password").doesNotExist())
        mockMvc.perform(get("/api/users/{id}", ann).with(asRole("USER", bob))).andExpect(status().isForbidden())
        mockMvc.perform(get("/api/users/{id}", ann).with(asRole("ADMIN", bob))).andExpect(status().isOk())
        mockMvc.perform(get("/api/users").with(asRole("USER", ann))).andExpect(status().isForbidden())
        mockMvc.perform(get("/api/users").with(asRole("ADMIN", bob))).andExpect(status().isOk())
        mockMvc.perform(get("/api/users/email/{email}", "ownership-ann@example.com").with(asRole("USER", ann)))
            .andExpect(status().isForbidden())
        mockMvc.perform(get("/api/users/email/{email}", "ownership-ann@example.com").with(asRole("ADMIN", bob)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.id").value(ann.toString()))
    }

    @Test
    fun notFoundMessageUsesUserIdRecordToString() {
        val missing = UUID.randomUUID()

        mockMvc.perform(get("/api/users/{id}", missing).with(asRole("ADMIN", UUID.randomUUID())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("\$.errorCode").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("\$.message").value("User not found with id: UserId[value=" + missing + "]"))
    }

    @Test
    fun updateKeepsPasswordAndRoleAndIncrementsVersion() {
        val ann = register("update-ann@example.com")
        val hashBefore = jdbc.queryForObject("select password from users where id = ?", String::class.java, ann)

        mockMvc.perform(
            put("/api/users/{id}", ann)
                .with(asRole("USER", ann)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"update-ann2@example.com\",\"firstname\":\"Anna\",\"lastname\":\"Lee\",\"age\":29}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.email").value("update-ann2@example.com"))
            .andExpect(jsonPath("\$.firstname").value("Anna"))
            .andExpect(jsonPath("\$.role").value("USER"))

        assertThat(jdbc.queryForObject("select password from users where id = ?", String::class.java, ann)).isEqualTo(hashBefore)
        assertThat(jdbc.queryForObject("select version from users where id = ?", Long::class.javaObjectType, ann)).isEqualTo(1L)
    }

    @Test
    fun adminCreateAndChangeRole() {
        val admin = UUID.randomUUID()

        val created = mockMvc.perform(
            post("/api/admin/users").with(asRole("ADMIN", admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"made-by-admin@example.com\",\"firstname\":\"Ad\",\"lastname\":\"Min\",\"age\":40,\"password\":\"Passw0rdX\",\"role\":\"ADMIN\"}"),
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("\$.role").value("ADMIN"))
            .andReturn().response.contentAsString
        val id = json(created).get("id").asString()

        mockMvc.perform(
            patch("/api/admin/users/{id}/role", id).with(asRole("ADMIN", admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.role").value("USER"))

        mockMvc.perform(
            patch("/api/admin/users/{id}/role", id).with(asRole("USER", admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"),
        )
            .andExpect(status().isForbidden())
        mockMvc.perform(
            post("/api/admin/users").with(asRole("USER", admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.com\",\"firstname\":\"A\",\"lastname\":\"B\",\"age\":40,\"password\":\"Passw0rdX\",\"role\":\"ADMIN\"}"),
        )
            .andExpect(status().isForbidden())
    }

    @Test
    fun deleteRemovesRowAndWritesUserDeletedOutboxRow() {
        val ann = register("delete-ann@example.com")

        mockMvc.perform(delete("/api/users/{id}", ann).with(asRole("USER", ann))).andExpect(status().isNoContent())

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Int::class.javaObjectType, ann)).isZero()
        val payloads = jdbc.queryForList(
            "select payload from outbox_message where event_type = 'user.deleted' and payload like ?", String::class.java, "%" + ann + "%",
        )
        assertThat(payloads).hasSize(1)
        assertThat(payloads[0]).contains("\"userId\":\"" + ann + "\"").contains("\"deletedAt\":")
        mockMvc.perform(get("/api/users/{id}", ann).with(asRole("ADMIN", UUID.randomUUID()))).andExpect(status().isNotFound())
    }

    @Test
    fun devSeederCreatedFixedAccounts() {
        assertThat(jdbc.queryForObject("select role from users where email = ?", String::class.java, ADMIN_EMAIL)).isEqualTo("ADMIN")
        assertThat(jdbc.queryForObject("select role from users where email = ?", String::class.java, "user@ecommerce.dev")).isEqualTo("USER")
    }

    @Test
    fun auditingFillsCreatedAtAndLastModified() {
        val id = register("audit@example.com")

        assertThat(jdbc.queryForObject("select created_at from users where id = ?", java.sql.Timestamp::class.java, id)).isNotNull()
        assertThat(jdbc.queryForObject("select last_modified from users where id = ?", java.sql.Timestamp::class.java, id)).isNotNull()
        assertThat(jdbc.queryForObject("select is_deleted from users where id = ?", Boolean::class.javaObjectType, id)).isFalse()
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@ecommerce.dev"
        private const val ADMIN_PASSWORD = "Admin123!"
    }
}
