package com.example.ecommerce.user.domain

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.exception.UnderageUserException
import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class UserDomainTest {

    @Test
    fun createBuildsUserWithEmptyIdAndEmailValueObject() {
        val user = User.create("john@example.com", "John", "Doe", 30, "hash", UserRole.USER)

        assertThat(user.id).isEqualTo(UserId.emptyValue())
        assertThat(user.email).isEqualTo(Email("john@example.com"))
        assertThat(user.firstname).isEqualTo("John")
        assertThat(user.lastname).isEqualTo("Doe")
        assertThat(user.age).isEqualTo(30)
        assertThat(user.password).isEqualTo("hash")
        assertThat(user.role).isEqualTo(UserRole.USER)
        assertThat(user.version).isNull()
        assertThat(user.isDeleted).isFalse()
    }

    @Test
    fun minimumAgeIsEighteen() {
        assertThat(User.MINIMUM_AGE).isEqualTo(18)
        assertThat(User.create("a@b.co", "A", "B", 18, "p", UserRole.USER).age).isEqualTo(18)
    }

    @Test
    fun underageUserIsRejectedWithMessage() {
        assertThatThrownBy { User.create("a@b.co", "A", "B", 17, "p", UserRole.USER) }
            .isInstanceOf(UnderageUserException::class.java)
            .hasMessage("User must be at least 18 years old, but was 17")
    }

    @Test
    fun nullAgeIsRejected() {
        assertThatThrownBy { User.create("a@b.co", "A", "B", null, "p", UserRole.USER) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Age cannot be null")
    }

    @Test
    fun blankFieldsAreRejectedWithMessages() {
        assertThatThrownBy { User.create("a@b.co", " ", "B", 20, "p", UserRole.USER) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Firstname cannot be null or blank")
        assertThatThrownBy { User.create("a@b.co", "A", null, 20, "p", UserRole.USER) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Lastname cannot be null or blank")
        assertThatThrownBy { User.create("a@b.co", "A", "B", 20, "", UserRole.USER) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Password cannot be null or blank")
        assertThatThrownBy { User.create("a@b.co", "A", "B", 20, "p", null) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Role cannot be null")
    }

    @Test
    fun invalidEmailIsRejected() {
        assertThatThrownBy { User.create("not-an-email", "A", "B", 20, "p", UserRole.USER) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Email is not valid")
    }

    @Test
    fun reconstituteDoesNotEnforceMinimumAge() {
        val id = UUID.randomUUID()
        val now = LocalDateTime.of(2024, 1, 1, 0, 0)

        val user = User.reconstitute(UserId.of(id), now, id, now, id, 4L, true, "a@b.co", "A", "B", 10, "p", UserRole.ADMIN)

        assertThat(user.id!!.value).isEqualTo(id)
        assertThat(user.age).isEqualTo(10)
        assertThat(user.isDeleted).isTrue()
        assertThat(user.version).isEqualTo(4L)
        assertThat(user.role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    fun emailValueObjectRules() {
        assertThat(Email.of("john.doe+tag@example.co.uk").value).isEqualTo("john.doe+tag@example.co.uk")
        assertThatThrownBy { Email.of(null) }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Email cannot be null or blank")
        assertThatThrownBy { Email.of("  ") }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Email cannot be null or blank")
        // Uppercase letters are rejected by the regex (current behavior).
        assertThatThrownBy { Email.of("John@Example.com") }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Email is not valid")
        assertThatThrownBy { Email.of("a@b") }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Email is not valid")
    }

    @Test
    fun userIdFactories() {
        val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

        assertThat(UserId.of(uuid)).isEqualTo(UserId.of("11111111-1111-1111-1111-111111111111"))
        assertThat(UserId.emptyValue().value).isNull()
        assertThat(UserId.of(uuid).toString()).isEqualTo("UserId[value=11111111-1111-1111-1111-111111111111]")
    }
}
