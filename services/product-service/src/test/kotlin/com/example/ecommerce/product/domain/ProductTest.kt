package com.example.ecommerce.product.domain

import com.example.ecommerce.product.domain.enums.ProductCategory
import com.example.ecommerce.product.domain.exception.InvalidPriceException
import com.example.ecommerce.product.domain.model.Product
import com.example.ecommerce.product.domain.valueobject.ProductId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class ProductTest {

    @Test
    fun createBuildsProductWithEmptyIdAndNoAuditData() {
        val product = Product.create("iPhone", "desc", BigDecimal("10.50"), "/img.jpg", ProductCategory.PHONE)

        assertThat(product.id).isEqualTo(ProductId.emptyValue())
        assertThat(product.id!!.value).isNull()
        assertThat(product.name).isEqualTo("iPhone")
        assertThat(product.desc).isEqualTo("desc")
        assertThat(product.price).isEqualByComparingTo("10.50")
        assertThat(product.imagePath).isEqualTo("/img.jpg")
        assertThat(product.category).isEqualTo(ProductCategory.PHONE)
        assertThat(product.version).isNull()
        assertThat(product.createdAt).isNull()
        assertThat(product.isDeleted).isFalse()
    }

    @Test
    fun createRejectsZeroPriceWithMessage() {
        assertThatThrownBy { Product.create("n", "d", BigDecimal.ZERO, "i", ProductCategory.PHONE) }
            .isInstanceOf(InvalidPriceException::class.java)
            .hasMessage("Price must be greater than zero, but was 0")
    }

    @Test
    fun createRejectsNegativePrice() {
        assertThatThrownBy { Product.create("n", "d", BigDecimal("-1.5"), "i", ProductCategory.PHONE) }
            .isInstanceOf(InvalidPriceException::class.java)
            .hasMessage("Price must be greater than zero, but was -1.5")
    }

    @Test
    fun createRejectsNullPrice() {
        assertThatThrownBy { Product.create("n", "d", null, "i", ProductCategory.PHONE) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Price cannot be null")
    }

    @Test
    fun reconstituteKeepsAllFieldsAndDoesNotValidatePositivePrice() {
        val id = UUID.randomUUID()
        val by = UUID.randomUUID()
        val now = LocalDateTime.of(2024, 1, 2, 3, 4, 5)

        val product = Product.reconstitute(
            ProductId.of(id), now, by, now.plusDays(1), by, 7L, true,
            "n", "d", BigDecimal.ZERO, "i", ProductCategory.COMPUTER,
        )

        assertThat(product.id).isEqualTo(ProductId(id))
        assertThat(product.createdAt).isEqualTo(now)
        assertThat(product.createdBy).isEqualTo(by)
        assertThat(product.lastModified).isEqualTo(now.plusDays(1))
        assertThat(product.lastModifiedBy).isEqualTo(by)
        assertThat(product.version).isEqualTo(7L)
        assertThat(product.isDeleted).isTrue()
        assertThat(product.price).isEqualByComparingTo("0")
        assertThat(product.category).isEqualTo(ProductCategory.COMPUTER)
    }

    @Test
    fun reconstituteRejectsNullPrice() {
        assertThatThrownBy {
            Product.reconstitute(
                ProductId.emptyValue(), null, null, null, null, null, false,
                "n", "d", null, "i", ProductCategory.PHONE,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Price cannot be null")
    }

    @Test
    fun productIdFactoriesEqualityAndToString() {
        val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

        assertThat(ProductId.of(uuid)).isEqualTo(ProductId.of("11111111-1111-1111-1111-111111111111"))
        assertThat(ProductId.of(uuid).hashCode()).isEqualTo(ProductId(uuid).hashCode())
        assertThat(ProductId.of(uuid).toString()).isEqualTo("ProductId[value=11111111-1111-1111-1111-111111111111]")
        assertThat(ProductId.emptyValue().toString()).isEqualTo("ProductId[value=null]")
        assertThatThrownBy { ProductId.of("not-a-uuid") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
