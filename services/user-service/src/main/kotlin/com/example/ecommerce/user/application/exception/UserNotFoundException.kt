package com.example.ecommerce.user.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

/**
 * Extends buildingblocks' shared [NotFoundException] base (same
 * convention as product-service's `ProductNotFoundException`).
 */
class UserNotFoundException(message: String?) : NotFoundException("USER_NOT_FOUND", message)
