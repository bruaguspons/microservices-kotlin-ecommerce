package com.example.ecommerce.user.infrastructure.persistence.mapper

import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import com.example.ecommerce.user.infrastructure.persistence.entity.UserEntity
import org.springframework.stereotype.Component

@Component
class UserPersistenceMapper {

    fun toEntity(user: User): UserEntity = UserEntity(
        id = user.id!!.value,
        createdAt = user.createdAt,
        createdBy = user.createdBy,
        lastModified = user.lastModified,
        lastModifiedBy = user.lastModifiedBy,
        version = user.version,
        isDeleted = user.isDeleted,
        email = user.email!!.value,
        firstname = user.firstname,
        lastname = user.lastname,
        age = user.age,
        password = user.password,
        role = user.role,
    )

    fun toDomain(user: UserEntity): User = User.reconstitute(
        UserId.of(user.id),
        user.createdAt,
        user.createdBy,
        user.lastModified,
        user.lastModifiedBy,
        user.version,
        user.isDeleted,
        user.email,
        user.firstname,
        user.lastname,
        user.age,
        user.password,
        user.role,
    )
}
