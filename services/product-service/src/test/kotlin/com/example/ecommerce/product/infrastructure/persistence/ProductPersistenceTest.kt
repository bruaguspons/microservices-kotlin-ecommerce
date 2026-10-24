package com.example.ecommerce.product.infrastructure.persistence

import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import com.example.ecommerce.product.infrastructure.persistence.mapper.ProductPersistenceMapper
import com.example.ecommerce.product.infrastructure.persistence.repository.JpaProductRepository
import com.example.ecommerce.product.infrastructure.persistence.repository.ProductRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
@Import(ProductRepository::class, ProductPersistenceMapper::class)
class ProductPersistenceTest {

    @Autowired
    private lateinit var repository: ProductRepository

    @Autowired
    private lateinit var jpaRepository: JpaProductRepository

    @Autowired
    private lateinit var mapper: ProductPersistenceMapper

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun newProduct(name: String): Product =
        Product.create(name, "desc", BigDecimal("10.50"), "/img.jpg", ProductCategory.PHONE)

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun saveAssignsIdVersionAndAuditDatesThenReadsBack() {
        val saved = repository.save(newProduct("iPhone"))
        flushAndClear()

        assertThat(saved.id!!.value).isNotNull()
        assertThat(saved.version).isNotNull()
        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.lastModified).isNotNull()
        assertThat(saved.isDeleted).isFalse()

        val found = repository.findById(saved.id!!).orElseThrow()
        assertThat(found.name).isEqualTo("iPhone")
        assertThat(found.desc).isEqualTo("desc")
        assertThat(found.price).isEqualByComparingTo("10.50")
        assertThat(found.imagePath).isEqualTo("/img.jpg")
        assertThat(found.category).isEqualTo(ProductCategory.PHONE)
        assertThat(found.isDeleted).isFalse()
    }

    @Test
    fun isDeletedIsPersistedInIsDeletedColumn() {
        val saved = repository.save(newProduct("a"))
        flushAndClear()

        val flag: Boolean? = jdbc.queryForObject(
            "select is_deleted from products where id = ?", Boolean::class.javaObjectType, saved.id!!.value,
        )

        assertThat(flag).isFalse()
    }

    @Test
    fun mapperRoundTripKeepsDeletedFlagAndIds() {
        val id = UUID.randomUUID()
        val domain = Product.reconstitute(
            ProductId.of(id), null, null, null, null, null, true,
            "n", "d", BigDecimal("5.00"), "i", ProductCategory.COMPUTER,
        )

        val entity = mapper.toEntity(domain)
        val back = mapper.toDomain(entity)

        assertThat(entity.id).isEqualTo(id)
        assertThat(entity.isDeleted).isTrue()
        assertThat(entity.category).isEqualTo(ProductCategory.COMPUTER)
        assertThat(back.id).isEqualTo(ProductId.of(id))
        assertThat(back.isDeleted).isTrue()
        assertThat(back.price).isEqualByComparingTo("5.00")
    }

    @Test
    fun softDeleteFlagRowIsStillVisibleBecauseFilterIsNotEnabledCurrentBehavior() {
        val deleted = Product.reconstitute(
            ProductId.emptyValue(), null, null, null, null, null, true,
            "gone", "d", BigDecimal("5.00"), "i", ProductCategory.PHONE,
        )
        val saved = repository.save(deleted)
        flushAndClear()

        // The softDeleteFilter is declared on BaseEntity but never enabled on the session,
        // so a row with is_deleted=true is still returned by reads.
        assertThat(saved.isDeleted).isTrue()
        assertThat(repository.findById(saved.id!!)).isPresent()
        assertThat(repository.findAll().map { it.name }).contains("gone")
    }

    @Test
    fun deleteByIdRemovesTheRowPhysicallyCurrentBehavior() {
        val saved = repository.save(newProduct("to-delete"))
        flushAndClear()

        repository.deleteById(saved.id!!)
        flushAndClear()

        assertThat(repository.findById(saved.id!!)).isEmpty()
        val rows = jdbc.queryForObject(
            "select count(*) from products where id = ?", Int::class.javaObjectType, saved.id!!.value,
        )
        assertThat(rows).isZero()
    }

    @Test
    fun updateIncrementsVersion() {
        val saved = repository.save(newProduct("v"))
        flushAndClear()
        val firstVersion = saved.version!!

        val updated = Product.reconstitute(
            saved.id, saved.createdAt, saved.createdBy, saved.lastModified,
            saved.lastModifiedBy, saved.version, saved.isDeleted, "v2", "d", BigDecimal("11.00"), "i", ProductCategory.PHONE,
        )
        val result = repository.save(updated)
        flushAndClear()

        assertThat(result.name).isEqualTo("v2")
        assertThat(jpaRepository.findById(saved.id!!.value!!).orElseThrow().version).isEqualTo(firstVersion + 1)
    }
}
