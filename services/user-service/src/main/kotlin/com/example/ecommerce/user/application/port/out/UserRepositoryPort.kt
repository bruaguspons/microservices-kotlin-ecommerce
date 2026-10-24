package com.example.ecommerce.user.application.port.out

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import java.util.Optional

interface UserRepositoryPort {
    fun findById(id: UserId): Optional<User>

    fun findByEmail(email: Email): Optional<User>

    fun findAll(): List<User>

    fun save(user: User): User

    fun deleteById(id: UserId)
}
