package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

class ShoppingCartNotFoundException(message: String?) : NotFoundException("SHOPPING_CART_NOT_FOUND", message)
