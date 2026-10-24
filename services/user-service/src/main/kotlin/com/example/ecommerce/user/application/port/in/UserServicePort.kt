package com.example.ecommerce.user.application.port.`in`

import com.example.ecommerce.user.application.dto.command.AdminCreateUserCommandDTO
import com.example.ecommerce.user.application.dto.command.ChangeUserRoleCommandDTO
import com.example.ecommerce.user.application.dto.command.RegisterUserCommandDTO
import com.example.ecommerce.user.application.dto.command.UpdateUserCommandDTO
import com.example.ecommerce.user.application.dto.result.UserResultDTO
import java.util.UUID

interface UserServicePort {
    fun findById(id: UUID): UserResultDTO

    fun findByEmail(email: String): UserResultDTO

    fun findAll(): List<UserResultDTO>

    fun register(user: RegisterUserCommandDTO): UserResultDTO

    fun update(id: UUID, user: UpdateUserCommandDTO): UserResultDTO

    fun deleteById(id: UUID)

    /**
     * ADMIN-only user creation with an explicit, arbitrary role.
     *
     * @param cmd the admin-supplied creation payload
     * @return the created user
     */
    fun createByAdmin(cmd: AdminCreateUserCommandDTO): UserResultDTO

    /**
     * ADMIN-only role change on an existing user.
     *
     * @param id the target user's id
     * @param cmd the new role
     * @return the updated user
     */
    fun changeRole(id: UUID, cmd: ChangeUserRoleCommandDTO): UserResultDTO
}
