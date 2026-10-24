package com.example.ecommerce.user.application.dto.result

import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.model.User
import java.util.UUID

data class UserResultDTO(
    val id: UUID?,
    val email: String?,
    val firstname: String?,
    val lastname: String?,
    val age: Int?,
    val role: UserRole?,
) {

    companion object {
        @JvmStatic
        fun from(user: User): UserResultDTO = UserResultDTO(
            user.id!!.value,
            user.email!!.value,
            user.firstname,
            user.lastname,
            user.age,
            user.role,
        )
    }
}
