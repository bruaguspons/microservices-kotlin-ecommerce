package com.example.ecommerce.buildingblocks.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiExceptionTest {

    private class SampleNotFound : NotFoundException("SAMPLE_NOT_FOUND", "sample missing")

    private class SampleUnavailable : UnavailableException("SAMPLE_UNAVAILABLE", "sample down")

    @Test
    fun notFoundCarriesCodeAndMessage() {
        val ex = SampleNotFound()

        assertThat(ex).isInstanceOf(ApiException::class.java).isInstanceOf(RuntimeException::class.java)
        assertThat(ex.errorCode).isEqualTo("SAMPLE_NOT_FOUND")
        assertThat(ex.message).isEqualTo("sample missing")
    }

    @Test
    fun unavailableCarriesCodeAndMessage() {
        val ex = SampleUnavailable()

        assertThat(ex).isInstanceOf(ApiException::class.java)
        assertThat(ex).isNotInstanceOf(NotFoundException::class.java)
        assertThat(ex.errorCode).isEqualTo("SAMPLE_UNAVAILABLE")
        assertThat(ex.message).isEqualTo("sample down")
    }
}
