package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

/**
 * Thrown when `UserClientPort` receives a 404 from user-service
 * (see design: Sync Communication / Error mapping).
 */
class UserNotFoundException(message: String?) : NotFoundException("USER_NOT_FOUND", message)
