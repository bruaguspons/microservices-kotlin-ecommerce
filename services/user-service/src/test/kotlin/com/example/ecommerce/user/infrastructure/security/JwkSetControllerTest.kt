package com.example.ecommerce.user.infrastructure.security

import com.example.ecommerce.user.infrastructure.api.JwkSetController
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JwkSetControllerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun rsaKeyPropertiesDefaultKeyIdAndBinding() {
        assertThat(RsaKeyProperties().keyId).isEqualTo("user-service-key-1")
        assertThat(RsaKeyProperties().privateKeyPath).isNull()

        val source = MapConfigurationPropertySource(mapOf("jwt.private-key-path" to "/x/key.pem", "jwt.key-id" to "k2"))
        val bound = Binder(source).bind("jwt", RsaKeyProperties::class.java).get()
        assertThat(bound.privateKeyPath).isEqualTo("/x/key.pem")
        assertThat(bound.keyId).isEqualTo("k2")
    }

    @Test
    fun ephemeralKeyIsGeneratedWhenNoPathConfigured() {
        val provider = JwtKeyProvider(RsaKeyProperties())

        assertThat(provider.keyId).isEqualTo("user-service-key-1")
        assertThat(provider.publicKey.modulus.bitLength()).isEqualTo(2048)
        assertThat(provider.privateKey.algorithm).isEqualTo("RSA")
    }

    @Test
    fun keyIsLoadedFromPkcs8PemAndPublicKeyIsDerived() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        val file = tempDir.resolve("key.pem")
        Files.writeString(file, pem)
        val props = RsaKeyProperties()
        props.privateKeyPath = file.toString()
        props.keyId = "custom-kid"

        val provider = JwtKeyProvider(props)

        assertThat(provider.keyId).isEqualTo("custom-kid")
        assertThat(provider.publicKey.modulus).isEqualTo((pair.public as RSAPublicKey).modulus)
        assertThat(provider.publicKey.publicExponent).isEqualTo((pair.public as RSAPublicKey).publicExponent)
    }

    @Test
    fun jwksJsonShape() {
        val provider = JwtKeyProvider(RsaKeyProperties())
        val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(JwkSetController(provider)).build()

        val json = mockMvc.perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString

        val set = JWKSet.parse(json)
        assertThat(set.keys).hasSize(1)
        val key = set.keys[0] as RSAKey
        assertThat(key.keyID).isEqualTo("user-service-key-1")
        assertThat(key.keyUse.identifier()).isEqualTo("sig")
        assertThat(key.algorithm.name).isEqualTo("RS256")
        assertThat(key.keyType.value).isEqualTo("RSA")
        assertThat(key.isPrivate).isFalse()
        assertThat(key.toRSAPublicKey().modulus).isEqualTo(provider.publicKey.modulus)

        @Suppress("UNCHECKED_CAST")
        val parsed = tools.jackson.databind.json.JsonMapper().readValue(json, Map::class.java) as Map<String, Any?>
        assertThat(parsed).containsOnlyKeys("keys")

        @Suppress("UNCHECKED_CAST")
        val jwk = (parsed["keys"] as List<Map<String, Any?>>)[0]
        assertThat(jwk).containsOnlyKeys("kty", "use", "alg", "kid", "n", "e")
    }
}
