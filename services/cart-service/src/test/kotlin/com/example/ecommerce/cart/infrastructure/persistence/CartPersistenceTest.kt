package com.example.ecommerce.cart.infrastructure.persistence

import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.persistence.mapper.ProductShoppingCartPersistenceMapper
import com.example.ecommerce.cart.infrastructure.persistence.mapper.ShoppingCartPersistenceMapper
import com.example.ecommerce.cart.infrastructure.persistence.repository.ShoppingCartRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer

@DataJpaTest
@Import(ShoppingCartRepository::class, ShoppingCartPersistenceMapper::class, ProductShoppingCartPersistenceMapper::class)
class CartPersistenceTest {

    @Autowired
    private lateinit var repository: ShoppingCartRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun newCart(): ShoppingCart = repository.save(ShoppingCart.create(UserId.of(UUID.randomUUID())))

    @Test
    fun saveEmptyCartAssignsIdAndVersion() {
        val saved = newCart()
        flushAndClear()

        assertThat(saved.id!!.value).isNotNull()
        assertThat(saved.version).isNotNull()
        assertThat(saved.total).isEqualByComparingTo("0")
        assertThat(saved.payed).isFalse()
        assertThat(saved.isDeleted).isFalse()
        assertThat(saved.items).isEmpty()
        assertThat(repository.findById(saved.id!!)).isPresent()
    }

    @Test
    fun addItemsAndReadThemBackWithIdsPriceAndVersion() {
        val cart = newCart()
        flushAndClear()
        val loaded = repository.findById(cart.id!!).orElseThrow()
        loaded.addItem(P1, 2, BigDecimal("10.50"), 3L)
        loaded.addItem(P2, 1, BigDecimal("4.00"), 1L)

        val saved = repository.save(loaded)
        flushAndClear()

        assertThat(saved.items).hasSize(2)
        assertThat(saved.items).allSatisfy(Consumer { i -> assertThat(i.id!!.value).isNotNull() })
        val reread = repository.findById(cart.id!!).orElseThrow()
        assertThat(reread.total).isEqualByComparingTo("25.00")
        assertThat(reread.items!!.map { it.productId }).containsExactlyInAnyOrder(P1, P2)
        val first = reread.items!!.first { it.productId == P1 }
        assertThat(first.quantity).isEqualTo(2)
        assertThat(first.unitPrice).isEqualByComparingTo("10.50")
        assertThat(first.priceVersion).isEqualTo(3L)
        assertThat(first.shoppingCartId).isEqualTo(cart.id)
        assertThat(first.isDeleted).isFalse()
    }

    @Test
    fun isDeletedColumnExistsOnCartAndItemTables() {
        val cart = newCart()
        flushAndClear()
        val loaded = repository.findById(cart.id!!).orElseThrow()
        loaded.addItem(P1, 1, BigDecimal.ONE, 0L)
        repository.save(loaded)
        flushAndClear()

        assertThat(jdbc.queryForObject("select is_deleted from shopping_cart where id = ?", Boolean::class.javaObjectType, cart.id!!.value)).isFalse()
        assertThat(
            jdbc.queryForObject(
                "select count(*) from product_shopping_cart where is_deleted = false and shopping_cart_id = ?",
                Int::class.javaObjectType, cart.id!!.value,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun removingItemDeletesRowByOrphanRemoval() {
        val cart = newCart()
        flushAndClear()
        val loaded = repository.findById(cart.id!!).orElseThrow()
        loaded.addItem(P1, 1, BigDecimal.ONE, 0L)
        val saved = repository.save(loaded)
        flushAndClear()

        val again = repository.findById(saved.id!!).orElseThrow()
        again.removeItem(again.items!![0].id)
        repository.save(again)
        flushAndClear()

        assertThat(repository.findById(cart.id!!).orElseThrow().items).isEmpty()
        assertThat(
            jdbc.queryForObject("select count(*) from product_shopping_cart where shopping_cart_id = ?", Int::class.javaObjectType, cart.id!!.value),
        ).isZero()
    }

    @Test
    fun findOpenCartsContainingProductExcludesPaidCartsAndOtherProducts() {
        val open = newCart()
        val paid = newCart()
        val other = newCart()
        flushAndClear()
        for (id in arrayOf(open.id!!, paid.id!!)) {
            val c = repository.findById(id).orElseThrow()
            c.addItem(P1, 1, BigDecimal.TEN, 1L)
            repository.save(c)
        }
        val o = repository.findById(other.id!!).orElseThrow()
        o.addItem(P2, 1, BigDecimal.TEN, 1L)
        repository.save(o)
        flushAndClear()
        val toPay = repository.findById(paid.id!!).orElseThrow()
        toPay.pay()
        repository.save(toPay)
        flushAndClear()

        assertThat(repository.findOpenCartsContainingProduct(P1).map { it.id }).containsExactly(open.id)
        assertThat(repository.findOpenCartsContainingProduct(P2).map { it.id }).containsExactly(other.id)
    }

    @Test
    fun deleteByIdCascadesAndRemovesRowsPhysicallyCurrentBehavior() {
        val cart = newCart()
        flushAndClear()
        val loaded = repository.findById(cart.id!!).orElseThrow()
        loaded.addItem(P1, 1, BigDecimal.ONE, 0L)
        repository.save(loaded)
        flushAndClear()

        repository.deleteById(cart.id!!)
        flushAndClear()

        assertThat(repository.findById(cart.id!!)).isEmpty()
        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where id = ?", Int::class.javaObjectType, cart.id!!.value)).isZero()
        assertThat(
            jdbc.queryForObject("select count(*) from product_shopping_cart where shopping_cart_id = ?", Int::class.javaObjectType, cart.id!!.value),
        ).isZero()
    }

    @Test
    fun softDeleteFlagIsNotFilteredCurrentBehavior() {
        val flagged = ShoppingCart.reconstitute(
            ShoppingCartId.emptyValue(), null, null, null, null, null, true,
            BigDecimal.ZERO, false, UserId.of(UUID.randomUUID()), ArrayList(),
        )
        val saved = repository.save(flagged)
        flushAndClear()

        assertThat(saved.isDeleted).isTrue()
        assertThat(repository.findById(saved.id!!)).isPresent()
        assertThat(repository.findAll().map { it.id }).contains(saved.id)
    }

    companion object {
        private val P1: ProductId = ProductId.of(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
        private val P2: ProductId = ProductId.of(UUID.fromString("a0000000-0000-0000-0000-000000000002"))
    }
}
