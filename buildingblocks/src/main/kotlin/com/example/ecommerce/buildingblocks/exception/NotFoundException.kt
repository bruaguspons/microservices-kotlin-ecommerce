package com.example.ecommerce.buildingblocks.exception

/**
 * Base type for "resource does not exist" errors (e.g. a 404 response from a
 * cross-service WebClient call - see design: Sync Communication / Error mapping).
 */
abstract class NotFoundException protected constructor(errorCode: String?, message: String?) :
    ApiException(errorCode, message)
