package com.example.ecommerce.buildingblocks.contracts

import com.example.ecommerce.buildingblocks.contracts.cart.CartPaid
import com.example.ecommerce.buildingblocks.contracts.product.ProductPriceChanged
import com.example.ecommerce.buildingblocks.contracts.user.UserDeleted
import com.example.ecommerce.buildingblocks.contracts.user.UserRegistered
import com.example.ecommerce.buildingblocks.event.DomainEvent
import com.example.ecommerce.buildingblocks.event.EventRoutingKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Characterization of the event wire format: exact JSON, round trip and routing keys.
 * The JSON must never contain an `eventType` field.
 */
class EventContractsJsonTest {

    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun cartPaidSnapshotAndRoundTrip() {
        val event = CartPaid(ID_A, ID_B, BigDecimal("99.90"), INSTANT)

        val json = mapper.writeValueAsString(event)

        assertThat(json).isEqualTo(
            "{\"cartId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"userId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"totalAmount\":99.90,"
                + "\"paidAt\":\"2024-05-06T07:08:09.123456Z\"}",
        )
        assertThat(mapper.readValue(json, CartPaid::class.java)).isEqualTo(event)
        assertThat(event.eventType()).isEqualTo("cart.paid")
    }

    @Test
    fun productPriceChangedSnapshotAndRoundTrip() {
        val event = ProductPriceChanged(ID_A, BigDecimal("12.50"), 3L, INSTANT)

        val json = mapper.writeValueAsString(event)

        assertThat(json).isEqualTo(
            "{\"productId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"newPrice\":12.50,"
                + "\"version\":3,"
                + "\"changedAt\":\"2024-05-06T07:08:09.123456Z\"}",
        )
        assertThat(mapper.readValue(json, ProductPriceChanged::class.java)).isEqualTo(event)
        assertThat(event.eventType()).isEqualTo("product.price-changed")
    }

    @Test
    fun userRegisteredSnapshotAndRoundTrip() {
        val event = UserRegistered(ID_A, "john@example.com", INSTANT)

        val json = mapper.writeValueAsString(event)

        assertThat(json).isEqualTo(
            "{\"userId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"email\":\"john@example.com\","
                + "\"registeredAt\":\"2024-05-06T07:08:09.123456Z\"}",
        )
        assertThat(mapper.readValue(json, UserRegistered::class.java)).isEqualTo(event)
        assertThat(event.eventType()).isEqualTo("user.registered")
    }

    @Test
    fun userDeletedSnapshotAndRoundTrip() {
        val event = UserDeleted(ID_A, INSTANT)

        val json = mapper.writeValueAsString(event)

        assertThat(json).isEqualTo(
            "{\"userId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"deletedAt\":\"2024-05-06T07:08:09.123456Z\"}",
        )
        assertThat(mapper.readValue(json, UserDeleted::class.java)).isEqualTo(event)
        assertThat(event.eventType()).isEqualTo("user.deleted")
    }

    @Test
    fun jsonNeverContainsEventTypeField() {
        val events: Array<DomainEvent> = arrayOf(
            CartPaid(ID_A, ID_B, BigDecimal.ONE, INSTANT),
            ProductPriceChanged(ID_A, BigDecimal.ONE, 1L, INSTANT),
            UserRegistered(ID_A, "a@b.com", INSTANT),
            UserDeleted(ID_A, INSTANT),
        )
        for (event in events) {
            assertThat(mapper.writeValueAsString(event)).doesNotContain("eventType")
        }
    }

    /**
     * Jackson 3 defaults FAIL_ON_NULL_FOR_PRIMITIVES to true, so a missing or null
     * non-null `version` is rejected.
     */
    @Test
    fun missingPrimitiveVersionIsRejectedCurrentBehavior() {
        val json = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"newPrice\":1.00}"

        assertThatThrownBy { mapper.readValue(json, ProductPriceChanged::class.java) }
            .isInstanceOf(MismatchedInputException::class.java)
    }

    @Test
    fun nullPrimitiveVersionIsRejectedCurrentBehavior() {
        val json = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"newPrice\":1.00,\"version\":null}"

        assertThatThrownBy { mapper.readValue(json, ProductPriceChanged::class.java) }
            .isInstanceOf(MismatchedInputException::class.java)
    }

    @Test
    fun missingReferenceFieldsDeserializeAsNull() {
        val json = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"version\":2}"

        val event = mapper.readValue(json, ProductPriceChanged::class.java)

        assertThat(event.newPrice).isNull()
        assertThat(event.changedAt).isNull()
        assertThat(event.version).isEqualTo(2L)
    }

    @Test
    fun routingKeys() {
        assertThat(EventRoutingKeys.CART_PAID).isEqualTo("cart.paid")
        assertThat(EventRoutingKeys.PRODUCT_PRICE_CHANGED).isEqualTo("product.price-changed")
        assertThat(EventRoutingKeys.USER_REGISTERED).isEqualTo("user.registered")
        assertThat(EventRoutingKeys.USER_DELETED).isEqualTo("user.deleted")
    }

    companion object {
        private val ID_A: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        private val ID_B: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        private val INSTANT: Instant = Instant.parse("2024-05-06T07:08:09.123456Z")
    }
}
