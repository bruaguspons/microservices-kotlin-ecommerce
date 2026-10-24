package com.example.ecommerce.buildingblocks.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.security.oauth2.jwt.JwtDecoder

class JwtDecoderConfigTest {

    @Test
    fun jwtPropertiesBindJwksUri() {
        val source = MapConfigurationPropertySource(mapOf("jwt.jwks-uri" to "http://localhost:8083/.well-known/jwks.json"))

        val props = Binder(source).bind("jwt", JwtProperties::class.java).get()

        assertThat(props.jwksUri).isEqualTo("http://localhost:8083/.well-known/jwks.json")
    }

    @Test
    fun decoderBeanIsBuiltFromJwksUriWithoutNetworkAccess() {
        val props = JwtProperties()
        props.jwksUri = "http://localhost:1/.well-known/jwks.json"

        val decoder: JwtDecoder = JwtDecoderConfig().jwtDecoder(props)

        assertThat(decoder).isNotNull()
    }
}
