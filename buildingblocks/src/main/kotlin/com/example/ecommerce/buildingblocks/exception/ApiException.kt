package com.example.ecommerce.buildingblocks.exception

/**
 * Base type for shared cross-service API exceptions. Service-specific
 * subclasses (e.g. `ProductNotFoundException`, `UserUnavailableException`)
 * extend [NotFoundException] or [UnavailableException] in their
 * own module.
 */
abstract class ApiException protected constructor(
    val errorCode: String?,
    message: String?,
) : RuntimeException(message)
