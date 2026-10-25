package com.example.ecommerce.user.infrastructure.persistence

import com.example.ecommerce.shared.domain.valueobject.Email
import com.example.ecommerce.user.domain.enums.UserRole
import com.example.ecommerce.user.domain.model.User
import com.example.ecommerce.user.domain.valueobject.UserId
import com.example.ecommerce.user.infrastructure.persistence.mapper.UserPersistenceMapper
import com.example.ecommerce.user.infrastructure.persistence.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest
@Import(UserRepository::class, UserPersistenceMapper::class)
class UserPersistenceTest {

    @Autowired
    private lateinit var repository: UserRepository

    @Autowired
    private lateinit var mapper: UserPersistenceMapper

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun newUser(email: String): User =
        User.create(email, "John", "Doe", 30, "hash", UserRole.USER)

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun saveAndFindByIdAndEmail() {
        val saved = repository.save(newUser("john@example.com"))
        flushAndClear()

        assertThat(saved.id!!.value).isNotNull()
        assertThat(saved.version).isNotNull()
        assertThat(saved.isDeleted).isFalse()
        val byId = repository.findById(saved.id!!).orElseThrow()
        assertThat(byId.email).isEqualTo(Email("john@example.com"))
        assertThat(byId.firstname).isEqualTo("John")
        assertThat(byId.age).isEqualTo(30)
        assertThat(byId.role).isEqualTo(UserRole.USER)
        assertThat(repository.findByEmail(Email("john@example.com"))).isPresent()
        assertThat(repository.findByEmail(Email("other@example.com"))).isEmpty()
    }

    @Test
    fun isDeletedColumnIsPersisted() {
        val saved = repository.save(newUser("flag@example.com"))
        flushAndClear()

        val flag: Boolean? = jdbc.queryForObject(
            "select is_deleted from users where id = ?", Boolean::class.javaObjectType, saved.id!!.value,
        )

        assertThat(flag).isFalse()
    }

    @Test
    fun mapperRoundTripKeepsDeletedFlag() {
        val deleted = User.reconstitute(
            UserId.emptyValue(), null, null, null, null, null,
            true, "gone@example.com", "G", "O", 40, "hash", UserRole.ADMIN,
        )

        val entity = mapper.toEntity(deleted)
        val back = mapper.toDomain(entity)

        assertThat(entity.isDeleted).isTrue()
        assertThat(back.isDeleted).isTrue()
        assertThat(back.role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    fun rowsFlaggedAsDeletedAreInvisibleToReads() {
        val deleted = User.reconstitute(
            UserId.emptyValue(), null, null, null, null, null,
            true, "gone2@example.com", "G", "O", 40, "hash", UserRole.USER,
        )
        val saved = repository.save(deleted)
        flushAndClear()

        assertThat(repository.findById(saved.id!!)).isEmpty()
        assertThat(repository.findByEmail(Email("gone2@example.com"))).isEmpty()
        assertThat(repository.findAll().map { it.email!!.value }).doesNotContain("gone2@example.com")
    }

    @Test
    fun deleteByIdSoftDeletesTheRow() {
        val saved = repository.save(newUser("del@example.com"))
        flushAndClear()

        repository.deleteById(saved.id!!)
        flushAndClear()

        assertThat(repository.findById(saved.id!!)).isEmpty()
        assertThat(repository.findByEmail(Email("del@example.com"))).isEmpty()
        assertThat(
            jdbc.queryForObject("select is_deleted from users where id = ?", Boolean::class.javaObjectType, saved.id!!.value),
        ).isTrue()
    }

    @Test
    fun emailIsUnique() {
        repository.save(newUser("dup@example.com"))
        flushAndClear()

        assertThatThrownBy {
            repository.save(newUser("dup@example.com"))
            entityManager.flush()
        }.isInstanceOf(RuntimeException::class.java).hasRootCauseInstanceOf(java.sql.SQLException::class.java)
    }
}
