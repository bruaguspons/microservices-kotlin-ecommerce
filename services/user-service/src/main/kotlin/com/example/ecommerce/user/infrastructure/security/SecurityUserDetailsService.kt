package com.example.ecommerce.user.infrastructure.security

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.application.port.out.UserRepositoryPort
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Loads [SecurityUserDetails] for authentication, used only during
 * `POST /api/auth/login`.
 */
@Service
class SecurityUserDetailsService(
    private val userRepository: UserRepositoryPort,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(Email.of(username))
            .orElseThrow { UsernameNotFoundException("User not found with email: $username") }

        return SecurityUserDetails(
            user.id!!.value,
            user.email!!.value,
            user.password,
            user.role,
        )
    }
}
