package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

/**
 * Thrown when `ProductClientPort` receives a 404 from product-service
 * (see design: Sync Communication / Error mapping).
 */
class ProductNotFoundException(message: String?) : NotFoundException("PRODUCT_NOT_FOUND", message)
