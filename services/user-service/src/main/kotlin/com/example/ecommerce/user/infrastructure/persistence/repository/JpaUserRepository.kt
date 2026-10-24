package com.example.ecommerce.user.infrastructure.persistence.repository

import com.example.ecommerce.user.infrastructure.persistence.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface JpaUserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): Optional<UserEntity>

    fun existsByEmail(email: String): Boolean
}
