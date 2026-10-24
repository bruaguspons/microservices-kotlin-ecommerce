package com.example.ecommerce.user.infrastructure.api

import com.example.ecommerce.buildingblocks.jwt.JwtAuthConverter
import com.example.ecommerce.user.application.dto.command.AdminCreateUserCommandDTO
import com.example.ecommerce.user.application.dto.command.ChangeUserRoleCommandDTO
import com.example.ecommerce.user.application.dto.command.LoginCommandDTO
import com.example.ecommerce.user.application.dto.command.RegisterUserCommandDTO
import com.example.ecommerce.user.application.dto.command.UpdateUserCommandDTO
import com.example.ecommerce.user.application.dto.result.AuthResultDTO
import com.example.ecommerce.user.application.dto.result.UserResultDTO
import com.example.ecommerce.user.application.exception.InvalidCredentialsException
import com.example.ecommerce.user.application.exception.UserAlreadyExist
import com.example.ecommerce.user.application.exception.UserNotFoundException
import com.example.ecommerce.user.application.port.`in`.AuthServicePort
import com.example.ecommerce.user.application.port.`in`.UserServicePort
import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.exception.UnderageUserException
import com.example.ecommerce.user.infrastructure.security.SecurityConfig
import com.example.ecommerce.user.infrastructure.security.SecurityUserDetailsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(controllers = [UserController::class, AdminUserController::class, AuthController::class])
@EnableWebSecurity
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class UserControllersContractTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserServicePort

    @MockitoBean
    private lateinit var authService: AuthServicePort

    @MockitoBean
    private lateinit var userDetailsService: SecurityUserDetailsService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var jwtAuthConverter: JwtAuthConverter

    @MockitoBean(name = "jpaMappingContext")
    private lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private fun dto(): UserResultDTO = UserResultDTO(ID, "john@example.com", "John", "Doe", 30, UserRole.USER)

    @Test
    fun registerIsPublicReturns200() {
        whenever(userService.register(any<RegisterUserCommandDTO>())).thenReturn(dto())

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isOk())
            .andExpect(content().string(USER_JSON))

        verify(userService).register(RegisterUserCommandDTO("john@example.com", "John", "Doe", 30, "Passw0rdX"))
    }

    @Test
    fun registerWeakPasswordReturnsValidationError() {
        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"age\":30,\"password\":\"abcdefgh\"}"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"password: must match \\\"^(?=.*[a-z])(?=.*[A-Z])(?=.*\\\\d).+\$\\\"\"}"))
        verifyNoInteractions(userService)
    }

    @Test
    fun registerMissingFieldsListsEachFieldError() {
        val body = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn().response.contentAsString

        assertThat(body).startsWith("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"")
        assertThat(body)
            .contains("email: must not be blank")
            .contains("firstname: must not be blank")
            .contains("lastname: must not be blank")
            .contains("age: must not be null")
            .contains("password: must not be blank")
        assertThat(body.split("; ")).hasSize(5)
    }

    @Test
    fun duplicateUserIsConflict() {
        whenever(userService.register(any<RegisterUserCommandDTO>())).thenThrow(UserAlreadyExist("User alredy exists"))

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isConflict())
            .andExpect(content().string("{\"errorCode\":\"USER_ALREADY_EXISTS\",\"message\":\"User alredy exists\"}"))
    }

    @Test
    fun underageIsBadRequest() {
        whenever(userService.register(any<RegisterUserCommandDTO>()))
            .thenThrow(UnderageUserException("User must be at least 18 years old, but was 10"))

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"UNDERAGE_USER\",\"message\":\"User must be at least 18 years old, but was 10\"}"))
    }

    @Test
    fun illegalArgumentIsInvalidArgument() {
        whenever(userService.register(any<RegisterUserCommandDTO>())).thenThrow(IllegalArgumentException("Email is not valid"))

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"INVALID_ARGUMENT\",\"message\":\"Email is not valid\"}"))
    }

    @Test
    fun getByIdBindsUuidAndRequiresToken() {
        whenever(userService.findById(ID)).thenReturn(dto())

        mockMvc.perform(get("/api/users/{id}", ID)).andExpect(status().isUnauthorized())
        mockMvc.perform(get("/api/users/{id}", ID).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string(USER_JSON))
    }

    @Test
    fun getByIdNotFoundUsesUserIdToStringInMessage() {
        whenever(userService.findById(ID)).thenThrow(UserNotFoundException("User not found with id: UserId[value=" + ID + "]"))

        mockMvc.perform(get("/api/users/{id}", ID).with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(
                content().string(
                    "{\"errorCode\":\"USER_NOT_FOUND\","
                        + "\"message\":\"User not found with id: UserId[value=11111111-1111-1111-1111-111111111111]\"}",
                ),
            )
    }

    @Test
    fun malformedUuidPathVariableIsBadRequest() {
        mockMvc.perform(get("/api/users/not-a-uuid").with(jwt()))
            .andExpect(status().isBadRequest())
        verifyNoInteractions(userService)
    }

    @Test
    fun getByEmailReturnsUser() {
        whenever(userService.findByEmail("john@example.com")).thenReturn(dto())

        mockMvc.perform(get("/api/users/email/{email}", "john@example.com").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string(USER_JSON))
    }

    @Test
    fun getAllReturnsArray() {
        whenever(userService.findAll()).thenReturn(listOf(dto()))

        mockMvc.perform(get("/api/users").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(content().string("[" + USER_JSON + "]"))
    }

    @Test
    fun updateBindsIdAndBody() {
        whenever(userService.update(any<UUID>(), any<UpdateUserCommandDTO>())).thenReturn(dto())

        mockMvc.perform(
            put("/api/users/{id}", ID).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"age\":30}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(USER_JSON))

        verify(userService).update(ID, UpdateUserCommandDTO("john@example.com", "John", "Doe", 30))
    }

    @Test
    fun deleteReturns204() {
        mockMvc.perform(delete("/api/users/{id}", ID).with(jwt()))
            .andExpect(status().isNoContent())

        verify(userService).deleteById(ID)
    }

    @Test
    fun adminCreateReturns201() {
        whenever(userService.createByAdmin(any<AdminCreateUserCommandDTO>())).thenReturn(dto())

        mockMvc.perform(
            post("/api/admin/users").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"age\":30,\"password\":\"Passw0rdX\",\"role\":\"USER\"}"),
        )
            .andExpect(status().isCreated())
            .andExpect(content().string(USER_JSON))
    }

    @Test
    fun adminCreateWithoutTokenIsUnauthorized() {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun changeRolePatch() {
        whenever(userService.changeRole(any<UUID>(), any<ChangeUserRoleCommandDTO>())).thenReturn(dto())

        mockMvc.perform(
            patch("/api/admin/users/{id}/role", ID).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string(USER_JSON))

        verify(userService).changeRole(ID, ChangeUserRoleCommandDTO(UserRole.ADMIN))
    }

    @Test
    fun changeRoleMissingRoleIsValidationError() {
        mockMvc.perform(patch("/api/admin/users/{id}/role", ID).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"role: must not be null\"}"))
    }

    @Test
    fun loginIsPublicAndReturnsToken() {
        whenever(authService.login(any<LoginCommandDTO>())).thenReturn(AuthResultDTO.of("tok"))

        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"password\":\"x\"}"),
        )
            .andExpect(status().isOk())
            .andExpect(content().string("{\"token\":\"tok\"}"))
    }

    @Test
    fun invalidCredentialsIs401WithEnvelope() {
        whenever(authService.login(any<LoginCommandDTO>())).thenThrow(InvalidCredentialsException("Invalid email or password"))

        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john@example.com\",\"password\":\"x\"}"),
        )
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("{\"errorCode\":\"INVALID_CREDENTIALS\",\"message\":\"Invalid email or password\"}"))
    }

    @Test
    fun loginBlankBodyIsValidationError() {
        val body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn().response.contentAsString

        assertThat(body)
            .contains("\"errorCode\":\"VALIDATION_ERROR\"")
            .contains("email: must not be blank")
            .contains("password: must not be blank")
    }

    companion object {
        private val ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private const val USER_JSON =
            "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"email\":\"john@example.com\"," +
                "\"firstname\":\"John\",\"lastname\":\"Doe\",\"age\":30,\"role\":\"USER\"}"
        private const val REGISTER_BODY =
            "{\"email\":\"john@example.com\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"age\":30,\"password\":\"Passw0rdX\"}"
    }
}
