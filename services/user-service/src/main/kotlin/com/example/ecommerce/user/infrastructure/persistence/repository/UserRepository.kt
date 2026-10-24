package com.example.ecommerce.user.infrastructure.persistence.repository

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.application.port.out.UserRepositoryPort
import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import com.example.ecommerce.user.infrastructure.persistence.mapper.UserPersistenceMapper
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class UserRepository(
    private val userRepository: JpaUserRepository,
    private val userPersistenceMapper: UserPersistenceMapper,
) : UserRepositoryPort {

    override fun findById(id: UserId): Optional<User> =
        userRepository.findById(id.value!!).map { userPersistenceMapper.toDomain(it) }

    override fun findByEmail(email: Email): Optional<User> =
        userRepository.findByEmail(email.value!!).map { userPersistenceMapper.toDomain(it) }

    override fun findAll(): List<User> =
        userRepository.findAll().map { userPersistenceMapper.toDomain(it) }

    override fun save(user: User): User =
        userPersistenceMapper.toDomain(userRepository.save(userPersistenceMapper.toEntity(user)))

    override fun deleteById(id: UserId) {
        userRepository.deleteById(id.value!!)
    }
}
