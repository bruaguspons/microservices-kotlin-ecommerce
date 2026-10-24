package com.example.ecommerce.cart.domain

import com.example.ecommerce.cart.domain.exception.CartAlreadyPaidException
import com.example.ecommerce.cart.domain.model.ProductShoppingCart
import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ProductShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class ShoppingCartDomainTest {

    /** Items created through the aggregate get an empty id; give them distinct ids by reconstituting the cart. */
    @Suppress("unused")
    private fun cartWithItems(payed: Boolean, vararg items: ProductShoppingCart): ShoppingCart {
        var total = BigDecimal.ZERO
        for (item in items) {
            total = total.add(item.unitPrice!!.multiply(BigDecimal.valueOf(item.quantity!!.toLong())))
        }
        return ShoppingCart.reconstitute(
            ShoppingCartId.of(UUID.randomUUID()), null, null, null, null, 0L, false,
            total, payed, USER, ArrayList(listOf(*items)),
        )
    }

    private fun item(cartId: ShoppingCartId, productId: ProductId, qty: Int, price: String, priceVersion: Long): ProductShoppingCart =
        ProductShoppingCart.reconstitute(
            ProductShoppingCartId.of(UUID.randomUUID()), null, null, null, null, 0L, false,
            productId, cartId, qty, BigDecimal(price), priceVersion,
        )

    @Test
    fun createStartsEmptyUnpaidWithZeroTotal() {
        val cart = ShoppingCart.create(USER)

        assertThat(cart.id).isEqualTo(ShoppingCartId.emptyValue())
        assertThat(cart.total).isEqualTo(BigDecimal.ZERO)
        assertThat(cart.payed).isFalse()
        assertThat(cart.userId).isEqualTo(USER)
        assertThat(cart.items).isEmpty()
        assertThat(cart.isDeleted).isFalse()
    }

    @Test
    fun createRejectsNullUser() {
        assertThatThrownBy { ShoppingCart.create(null) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("UserId cannot be null")
    }

    @Test
    fun addItemRecalculatesTotalAndKeepsPriceVersion() {
        val cart = ShoppingCart.create(USER)

        val first = cart.addItem(P1, 2, BigDecimal("10.50"), 3L)
        cart.addItem(P2, 1, BigDecimal("4.00"), 1L)

        assertThat(first.productId).isEqualTo(P1)
        assertThat(first.quantity).isEqualTo(2)
        assertThat(first.unitPrice).isEqualByComparingTo("10.50")
        assertThat(first.priceVersion).isEqualTo(3L)
        assertThat(first.id).isEqualTo(ProductShoppingCartId.emptyValue())
        assertThat(cart.items).hasSize(2)
        assertThat(cart.total).isEqualByComparingTo("25.00")
    }

    @Test
    fun itemValidation() {
        val cart = ShoppingCart.create(USER)

        assertThatThrownBy { cart.addItem(P1, null, BigDecimal.ONE, 0L) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Quantity cannot be null")
        assertThatThrownBy { cart.addItem(P1, 0, BigDecimal.ONE, 0L) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Quantity must be greater than zero, but was 0")
        assertThatThrownBy { cart.addItem(P1, 1, null, 0L) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Unit price cannot be null")
        assertThat(cart.items).isEmpty()
    }

    @Test
    fun updateItemQuantityRecalculatesTotal() {
        val cartId = ShoppingCartId.of(UUID.randomUUID())
        val line = item(cartId, P1, 1, "10.00", 0L)
        val cart = ShoppingCart.reconstitute(
            cartId, null, null, null, null, 0L, false, BigDecimal("10.00"), false, USER,
            ArrayList(listOf(line)),
        )

        val updated = cart.updateItemQuantity(line.id, 4)

        assertThat(updated.quantity).isEqualTo(4)
        assertThat(cart.total).isEqualByComparingTo("40.00")
        assertThatThrownBy { cart.updateItemQuantity(line.id, 0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Quantity must be greater than zero, but was 0")
    }

    @Test
    fun unknownItemIsRejectedWithIdInMessage() {
        val cart = ShoppingCart.create(USER)
        val missing = ProductShoppingCartId.of("b0000000-0000-0000-0000-000000000001")

        assertThatThrownBy { cart.removeItem(missing) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Item not found in cart with id: ProductShoppingCartId[value=b0000000-0000-0000-0000-000000000001]")
        assertThatThrownBy { cart.updateItemQuantity(missing, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Item not found in cart with id: ProductShoppingCartId[value=b0000000-0000-0000-0000-000000000001]")
    }

    @Test
    fun removeItemRecalculatesTotal() {
        val cartId = ShoppingCartId.of(UUID.randomUUID())
        val a = item(cartId, P1, 1, "10.00", 0L)
        val b = item(cartId, P2, 2, "5.00", 0L)
        val cart = ShoppingCart.reconstitute(
            cartId, null, null, null, null, 0L, false, BigDecimal("20.00"), false, USER,
            ArrayList(listOf(a, b)),
        )

        cart.removeItem(a.id)

        assertThat(cart.items).containsExactly(b)
        assertThat(cart.total).isEqualByComparingTo("10.00")
    }

    @Test
    fun payMarksCartPaidAndBlocksFurtherChanges() {
        val cart = ShoppingCart.create(USER)
        cart.pay()

        assertThat(cart.payed).isTrue()
        val message = "Cannot modify a shopping cart that has already been paid"
        assertThatThrownBy { cart.pay() }.isInstanceOf(CartAlreadyPaidException::class.java).hasMessage(message)
        assertThatThrownBy { cart.addItem(P1, 1, BigDecimal.ONE, 0L) }.isInstanceOf(CartAlreadyPaidException::class.java).hasMessage(message)
        assertThatThrownBy { cart.removeItem(ProductShoppingCartId.emptyValue()) }.isInstanceOf(CartAlreadyPaidException::class.java)
        assertThatThrownBy { cart.updateItemQuantity(ProductShoppingCartId.emptyValue(), 1) }.isInstanceOf(CartAlreadyPaidException::class.java)
        assertThatThrownBy { cart.updatePriceForProduct(P1, BigDecimal.TEN, 1L) }.isInstanceOf(CartAlreadyPaidException::class.java)
    }

    @Test
    fun updatePriceAppliesOnlyWhenVersionIsNotOlder() {
        val cartId = ShoppingCartId.of(UUID.randomUUID())
        val line = item(cartId, P1, 2, "10.00", 5L)
        val other = item(cartId, P2, 1, "1.00", 0L)
        val cart = ShoppingCart.reconstitute(
            cartId, null, null, null, null, 0L, false, BigDecimal("21.00"), false, USER,
            ArrayList(listOf(line, other)),
        )

        cart.updatePriceForProduct(P1, BigDecimal("99.00"), 4L)
        assertThat(line.unitPrice).isEqualByComparingTo("10.00")
        assertThat(cart.total).isEqualByComparingTo("21.00")

        cart.updatePriceForProduct(P1, BigDecimal("12.00"), 5L)
        assertThat(line.unitPrice).isEqualByComparingTo("12.00")
        assertThat(line.priceVersion).isEqualTo(5L)
        assertThat(cart.total).isEqualByComparingTo("25.00")

        cart.updatePriceForProduct(P1, BigDecimal("15.00"), 9L)
        assertThat(line.unitPrice).isEqualByComparingTo("15.00")
        assertThat(line.priceVersion).isEqualTo(9L)
        assertThat(cart.total).isEqualByComparingTo("31.00")
        assertThat(other.unitPrice).isEqualByComparingTo("1.00")
    }

    @Test
    fun reconstituteKeepsPaidFlagAndRequiresUser() {
        val paid = ShoppingCart.reconstitute(
            ShoppingCartId.of(UUID.randomUUID()), null, null, null, null, 2L, true,
            BigDecimal("1.00"), true, USER, ArrayList(),
        )

        assertThat(paid.payed).isTrue()
        assertThat(paid.version).isEqualTo(2L)
        assertThatThrownBy {
            ShoppingCart.reconstitute(
                ShoppingCartId.emptyValue(), null, null, null, null, null, false,
                BigDecimal.ZERO, false, null, ArrayList(),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessage("UserId cannot be null")
    }

    @Test
    fun valueObjectFactoriesEqualityAndToString() {
        val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

        assertThat(ShoppingCartId.of(uuid)).isEqualTo(ShoppingCartId.of(uuid.toString()))
        assertThat(ShoppingCartId.emptyValue().value).isNull()
        assertThat(ShoppingCartId.of(uuid).toString()).isEqualTo("ShoppingCartId[value=11111111-1111-1111-1111-111111111111]")
        assertThat(ProductId.of(uuid).toString()).isEqualTo("ProductId[value=11111111-1111-1111-1111-111111111111]")
        assertThat(UserId.of(uuid).toString()).isEqualTo("UserId[value=11111111-1111-1111-1111-111111111111]")
        assertThat(ProductShoppingCartId.of(uuid).toString()).isEqualTo("ProductShoppingCartId[value=11111111-1111-1111-1111-111111111111]")
        assertThat(ProductId.emptyValue().value).isNull()
        assertThat(UserId.emptyValue().value).isNull()
        assertThat(ProductShoppingCartId.emptyValue().value).isNull()
    }

    companion object {
        private val USER: UserId = UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"))
        private val P1: ProductId = ProductId.of(UUID.fromString("a0000000-0000-0000-0000-000000000001"))
        private val P2: ProductId = ProductId.of(UUID.fromString("a0000000-0000-0000-0000-000000000002"))
    }
}
