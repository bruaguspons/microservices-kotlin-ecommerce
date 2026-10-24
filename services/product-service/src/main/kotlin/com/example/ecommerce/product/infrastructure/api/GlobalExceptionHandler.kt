package com.example.ecommerce.product.infrastructure.api

import com.example.ecommerce.buildingblocks.dto.ErrorResponseDTO
import com.example.ecommerce.buildingblocks.exception.NotFoundException
import com.example.ecommerce.product.domain.exception.InvalidPriceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponseDTO> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponseDTO.of(ex.errorCode, ex.message))

    @ExceptionHandler(InvalidPriceException::class)
    fun handleInvalidPrice(ex: InvalidPriceException): ResponseEntity<ErrorResponseDTO> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponseDTO.of("INVALID_PRICE", ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponseDTO> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { fe -> fe.field + ": " + fe.defaultMessage }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponseDTO.of("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponseDTO> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponseDTO.of("INVALID_ARGUMENT", ex.message))
}
