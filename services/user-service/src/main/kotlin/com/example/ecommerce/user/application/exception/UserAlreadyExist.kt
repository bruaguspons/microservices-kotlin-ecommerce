package com.example.ecommerce.user.application.exception

import com.example.ecommerce.buildingblocks.exception.ApiException

/**
 * Thrown when registering/creating a user whose email is already taken.
 * Extends buildingblocks' [ApiException] directly (not
 * `NotFoundException`/`UnavailableException`, since this is a
 * 409-conflict case) so the shared error-code contract still applies.
 */
class UserAlreadyExist(message: String?) : ApiException("USER_ALREADY_EXISTS", message)
