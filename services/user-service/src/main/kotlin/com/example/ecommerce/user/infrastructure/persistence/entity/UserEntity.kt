package com.example.ecommerce.user.infrastructure.persistence.entity

import com.example.ecommerce.shared.infrastructure.persistance.entity.BaseEntity
import com.example.ecommerce.user.domain.enums.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

/**
 * No longer holds a `shoppingCarts` back-reference (see design:
 * database-per-service - carts live in cart-service's own schema now).
 */
@Entity
@Table(name = "users")
class UserEntity(
    id: UUID? = null,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
    email: String? = null,
    firstname: String? = null,
    lastname: String? = null,
    age: Int? = null,
    password: String? = null,
    role: UserRole? = null,
) : BaseEntity(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    @field:NotBlank
    @field:jakarta.validation.constraints.Email
    @Column(unique = true, nullable = false)
    var email: String? = email
        protected set

    @field:NotBlank
    @Column(nullable = false)
    var firstname: String? = firstname
        protected set

    @field:NotBlank
    @Column(nullable = false)
    var lastname: String? = lastname
        protected set

    @Column(nullable = false)
    var age: Int? = age
        protected set

    @field:NotBlank
    @Column(nullable = false)
    var password: String? = password
        protected set

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var role: UserRole? = role
        protected set
}
