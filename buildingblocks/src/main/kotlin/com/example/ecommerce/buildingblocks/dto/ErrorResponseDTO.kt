package com.example.ecommerce.buildingblocks.dto

data class ErrorResponseDTO(
    val errorCode: String?,
    val message: String?,
) {

    companion object {
        @JvmStatic
        fun of(errorCode: String?, message: String?): ErrorResponseDTO = ErrorResponseDTO(errorCode, message)
    }
}
