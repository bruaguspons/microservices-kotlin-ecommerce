package com.example.ecommerce.user.domain.model

import com.example.ecommerce.shared.domain.model.BaseModel
import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.exception.UnderageUserException
import com.example.ecommerce.user.domain.valueobject.UserId
import java.time.LocalDateTime
import java.util.UUID

/**
 * User aggregate root. No longer holds a `shoppingCarts` back-reference:
 * with database-per-service, carts live in cart-service's own schema - there
 * is no in-process/DB-level relationship anymore (see design: "UserEntity.shoppingCarts
 * back-reference ceases to exist entirely").
 */
class User private constructor(
    id: UserId?,
    createdAt: LocalDateTime?,
    createdBy: UUID?,
    lastModified: LocalDateTime?,
    lastModifiedBy: UUID?,
    version: Long?,
    isDeleted: Boolean,
    email: Email?,
    firstname: String?,
    lastname: String?,
    age: Int?,
    password: String?,
    role: UserRole?,
) : BaseModel<UserId>(id, createdAt, createdBy, lastModified, lastModifiedBy, version, isDeleted) {

    var email: Email? = email
        private set

    var firstname: String? = firstname
        private set

    var lastname: String? = lastname
        private set

    var age: Int? = age
        private set

    var password: String? = password
        private set

    var role: UserRole? = role
        private set

    companion object {
        const val MINIMUM_AGE: Int = 18

        @JvmStatic
        fun create(
            email: String?,
            firstname: String?,
            lastname: String?,
            age: Int?,
            password: String?,
            role: UserRole?,
        ): User {
            validateAgeStructure(age)
            validateMinimumAge(age!!)
            validateFirstname(firstname)
            validateLastname(lastname)
            validatePassword(password)
            validateRole(role)
            return User(
                UserId.emptyValue(),
                null,
                null,
                null,
                null,
                null,
                false,
                Email.of(email),
                firstname,
                lastname,
                age,
                password,
                role,
            )
        }

        @JvmStatic
        fun reconstitute(
            id: UserId?,
            createdAt: LocalDateTime?,
            createdBy: UUID?,
            lastModified: LocalDateTime?,
            lastModifiedBy: UUID?,
            version: Long?,
            isDeleted: Boolean,
            email: String?,
            firstname: String?,
            lastname: String?,
            age: Int?,
            password: String?,
            role: UserRole?,
        ): User {
            validateAgeStructure(age)
            validateFirstname(firstname)
            validateLastname(lastname)
            validatePassword(password)
            validateRole(role)
            return User(
                id,
                createdAt,
                createdBy,
                lastModified,
                lastModifiedBy,
                version,
                isDeleted,
                Email.of(email),
                firstname,
                lastname,
                age,
                password,
                role,
            )
        }

        /**
         * Validates that age is structurally present.
         *
         * @param age the age to validate
         * @throws IllegalArgumentException if `age` is `null`
         */
        private fun validateAgeStructure(age: Int?) {
            if (age == null) {
                throw IllegalArgumentException("Age cannot be null")
            }
        }

        /**
         * Validates that age satisfies the minimum age business rule.
         *
         * @param age the age to validate
         * @throws UnderageUserException if `age` is below [MINIMUM_AGE]
         */
        private fun validateMinimumAge(age: Int) {
            if (age < MINIMUM_AGE) {
                throw UnderageUserException(
                    "User must be at least $MINIMUM_AGE years old, but was $age",
                )
            }
        }

        /**
         * Validates that the firstname is structurally present.
         *
         * @param firstname the firstname to validate
         * @throws IllegalArgumentException if `firstname` is `null` or blank
         */
        private fun validateFirstname(firstname: String?) {
            if (firstname == null || firstname.isBlank()) {
                throw IllegalArgumentException("Firstname cannot be null or blank")
            }
        }

        /**
         * Validates that the lastname is structurally present.
         *
         * @param lastname the lastname to validate
         * @throws IllegalArgumentException if `lastname` is `null` or blank
         */
        private fun validateLastname(lastname: String?) {
            if (lastname == null || lastname.isBlank()) {
                throw IllegalArgumentException("Lastname cannot be null or blank")
            }
        }

        /**
         * Validates that the password is structurally present.
         *
         * @param password the password to validate
         * @throws IllegalArgumentException if `password` is `null` or blank
         */
        private fun validatePassword(password: String?) {
            if (password == null || password.isBlank()) {
                throw IllegalArgumentException("Password cannot be null or blank")
            }
        }

        /**
         * Validates that the role is structurally present.
         *
         * @param role the role to validate
         * @throws IllegalArgumentException if `role` is `null`
         */
        private fun validateRole(role: UserRole?) {
            if (role == null) {
                throw IllegalArgumentException("Role cannot be null")
            }
        }
    }
}
