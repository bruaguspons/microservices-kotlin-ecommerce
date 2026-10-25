package com.example.ecommerce.user.application.service

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.application.dto.command.AdminCreateUserCommandDTO
import com.example.ecommerce.user.application.dto.command.ChangeUserRoleCommandDTO
import com.example.ecommerce.user.application.dto.command.RegisterUserCommandDTO
import com.example.ecommerce.user.application.dto.command.UpdateUserCommandDTO
import com.example.ecommerce.user.application.dto.result.UserResultDTO
import com.example.ecommerce.user.application.exception.UserAlreadyExist
import com.example.ecommerce.user.application.exception.UserNotFoundException
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import com.example.ecommerce.user.application.port.out.UserRepositoryPort
import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import com.example.ecommerce.user.infrastructure.event.UserDeletedEventPublisher
import com.example.ecommerce.user.infrastructure.event.UserRegisteredEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserServiceImpl(
    private val userRepository: UserRepositoryPort,
    private val passwordEncoder: PasswordEncoder,
    private val userRegisteredEventPublisher: UserRegisteredEventPublisher,
    private val userDeletedEventPublisher: UserDeletedEventPublisher,
) : UserServicePort {

    @PreAuthorize("hasRole('ADMIN') or @ownership.isSelf(#id)")
    @Transactional(readOnly = true)
    override fun findById(id: UUID): UserResultDTO {
        val userId = UserId.of(id)
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        return UserResultDTO.from(user)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    override fun findByEmail(email: String): UserResultDTO {
        val userEmail = Email.of(email)
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { UserNotFoundException("User not found with email: $userEmail") }

        return UserResultDTO.from(user)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    override fun findAll(): List<UserResultDTO> =
        userRepository.findAll().map { UserResultDTO.from(it) }

    @Transactional
    override fun register(user: RegisterUserCommandDTO): UserResultDTO {
        if (userRepository.findByEmail(Email.of(user.email)).isPresent) {
            throw UserAlreadyExist("User already exists")
        }

        val created = User.create(
            user.email,
            user.firstname,
            user.lastname,
            user.age,
            passwordEncoder.encode(user.password),
            UserRole.USER,
        )

        val saved = userRepository.save(created)
        userRegisteredEventPublisher.publish(saved)

        return UserResultDTO.from(saved)
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isSelf(#id)")
    @Transactional
    override fun update(id: UUID, user: UpdateUserCommandDTO): UserResultDTO {
        val userId = UserId.of(id)
        val existing = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val updated = User.reconstitute(
            existing.id,
            existing.createdAt,
            existing.createdBy,
            existing.lastModified,
            existing.lastModifiedBy,
            existing.version,
            existing.isDeleted,
            user.email,
            user.firstname,
            user.lastname,
            user.age,
            existing.password,
            existing.role,
        )

        return UserResultDTO.from(userRepository.save(updated))
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isSelf(#id)")
    @Transactional
    override fun deleteById(id: UUID) {
        val userId = UserId.of(id)
        userRepository.deleteById(userId)
        userDeletedEventPublisher.publish(userId)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    override fun createByAdmin(cmd: AdminCreateUserCommandDTO): UserResultDTO {
        if (userRepository.findByEmail(Email.of(cmd.email)).isPresent) {
            throw UserAlreadyExist("User already exists")
        }

        val created = User.create(
            cmd.email,
            cmd.firstname,
            cmd.lastname,
            cmd.age,
            passwordEncoder.encode(cmd.password),
            cmd.role,
        )

        val saved = userRepository.save(created)
        userRegisteredEventPublisher.publish(saved)

        return UserResultDTO.from(saved)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    override fun changeRole(id: UUID, cmd: ChangeUserRoleCommandDTO): UserResultDTO {
        val userId = UserId.of(id)
        val existing = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        val updated = User.reconstitute(
            existing.id,
            existing.createdAt,
            existing.createdBy,
            existing.lastModified,
            existing.lastModifiedBy,
            existing.version,
            existing.isDeleted,
            existing.email!!.value,
            existing.firstname,
            existing.lastname,
            existing.age,
            existing.password,
            cmd.role,
        )

        return UserResultDTO.from(userRepository.save(updated))
    }
}
