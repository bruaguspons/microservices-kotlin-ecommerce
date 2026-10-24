package com.example.ecommerce.buildingblocks.exception

/**
 * Base type for "temporarily unreachable" errors - timeout, connection
 * refused, or open circuit breaker - distinct from [NotFoundException]
 * so callers can tell "doesn't exist" apart from "unreachable right now"
 * (see design: Sync Communication / Error mapping).
 */
abstract class UnavailableException protected constructor(errorCode: String?, message: String?) :
    ApiException(errorCode, message)
