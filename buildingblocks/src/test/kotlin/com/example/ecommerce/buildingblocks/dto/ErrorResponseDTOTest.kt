package com.example.ecommerce.buildingblocks.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ErrorResponseDTOTest {

    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun serializesAsErrorCodeAndMessage() {
        val json = mapper.writeValueAsString(ErrorResponseDTO.of("NOT_FOUND", "nope"))

        assertThat(json).isEqualTo("{\"errorCode\":\"NOT_FOUND\",\"message\":\"nope\"}")
    }

    @Test
    fun nullMessageIsSerializedAsNull() {
        val json = mapper.writeValueAsString(ErrorResponseDTO.of("X", null))

        assertThat(json).isEqualTo("{\"errorCode\":\"X\",\"message\":null}")
    }

    @Test
    fun ofBuildsEqualValue() {
        assertThat(ErrorResponseDTO.of("A", "b")).isEqualTo(ErrorResponseDTO("A", "b"))
    }
}
