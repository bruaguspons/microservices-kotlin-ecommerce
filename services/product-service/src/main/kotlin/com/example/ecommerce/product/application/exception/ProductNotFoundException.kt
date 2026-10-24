package com.example.ecommerce.product.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

/**
 * Extends buildingblocks' shared [NotFoundException] base so the
 * buildingblocks error-code contract is reused across services.
 */
class ProductNotFoundException(message: String?) : NotFoundException("PRODUCT_NOT_FOUND", message)
