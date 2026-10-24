package com.example.ecommerce.cart.application.exception

import com.example.ecommerce.buildingblocks.exception.NotFoundException

class ProductShoppingCartNotFoundException(message: String?) :
    NotFoundException("PRODUCT_SHOPPING_CART_NOT_FOUND", message)
