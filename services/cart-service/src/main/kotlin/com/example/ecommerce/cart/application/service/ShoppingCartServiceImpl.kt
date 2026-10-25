package com.example.ecommerce.cart.application.service

import com.example.ecommerce.cart.application.dto.command.AddItemCommandDTO
import com.example.ecommerce.cart.application.dto.command.CreateShoppingCartDTO
import com.example.ecommerce.cart.application.dto.command.UpdateItemQuantityCommandDTO
import com.example.ecommerce.cart.application.dto.result.ProductShoppingCartResultDTO
import com.example.ecommerce.cart.application.dto.result.ShoppingCartResultDTO
import com.example.ecommerce.cart.application.exception.ProductShoppingCartNotFoundException
import com.example.ecommerce.cart.application.exception.ShoppingCartNotFoundException
import com.example.ecommerce.cart.application.exception.UserNotFoundException
import com.example.ecommerce.cart.application.port.`in`.ShoppingCartServicePort
import com.example.ecommerce.cart.application.port.out.ShoppingCartRepositoryPort
import com.example.ecommerce.cart.application.port.out.client.ProductClientPort
import com.example.ecommerce.cart.application.port.out.client.UserClientPort
import com.example.ecommerce.cart.domain.model.ShoppingCart
import com.example.ecommerce.cart.domain.valueobject.ProductId
import com.example.ecommerce.cart.domain.valueobject.ProductShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.ShoppingCartId
import com.example.ecommerce.cart.domain.valueobject.UserId
import com.example.ecommerce.cart.infrastructure.event.CartPaidEventPublisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ShoppingCartServiceImpl(
    private val repository: ShoppingCartRepositoryPort,
    private val productClient: ProductClientPort,
    private val userClient: UserClientPort,
    private val cartPaidEventPublisher: CartPaidEventPublisher,
    private val priceUpdateAttempt: CartPriceUpdateAttempt,
) : ShoppingCartServicePort {

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#id)")
    @Transactional(readOnly = true)
    override fun findById(id: UUID?): ShoppingCartResultDTO =
        ShoppingCartResultDTO.from(loadCart(id))

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    override fun findAll(): List<ShoppingCartResultDTO> =
        repository.findAll().map { ShoppingCartResultDTO.from(it) }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isSelf(#cmd.userId)")
    @Transactional
    override fun create(cmd: CreateShoppingCartDTO): ShoppingCartResultDTO {
        // Validate the user exists via user-service before persisting, per design:
        // "cart-service validates UserId exists via UserClientPort.exists(UserId) at
        // cart-creation time only (not on every operation, to avoid chatty calls)".
        val userId = UserId.of(cmd.userId)
        if (!userClient.exists(userId)) {
            throw UserNotFoundException("User not found with id: ${userId.value}")
        }

        val shoppingCart = ShoppingCart.create(userId)
        return ShoppingCartResultDTO.from(repository.save(shoppingCart))
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#id)")
    @Transactional
    override fun deleteById(id: UUID?) {
        repository.deleteById(ShoppingCartId.of(id))
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#id)")
    @Transactional
    override fun pay(id: UUID?): ShoppingCartResultDTO {
        val cart = loadCart(id)
        cart.pay()
        val saved = repository.save(cart)

        // Same transaction as the state change (see design: Payment Flow) - the outbox
        // row commits atomically with the cart row via the shared EventDispatcher.
        cartPaidEventPublisher.publish(saved)

        return ShoppingCartResultDTO.from(saved)
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#cartId)")
    @Transactional
    override fun addItem(cartId: UUID?, cmd: AddItemCommandDTO): ProductShoppingCartResultDTO {
        val cart = loadCart(cartId)
        val existingItemIds: Set<ProductShoppingCartId?> = cart.items!!.map { it.id }.toSet()

        val product = productClient.getById(ProductId.of(cmd.productId))!!
        cart.addItem(ProductId.of(cmd.productId), cmd.quantity, product.price, product.version)
        val saved = repository.save(cart)

        // The pre-save item carries a placeholder id (see ProductShoppingCart.create());
        // repository.save() maps the persisted entity back into a NEW domain aggregate,
        // so the real DB-generated id only exists on that returned instance. Find the item
        // by exclusion from the pre-save id set rather than reusing the stale reference.
        val savedItem = saved.items!!
            .firstOrNull { item -> item.id !in existingItemIds }
            ?: throw IllegalStateException("Newly added item not found after save")

        return ProductShoppingCartResultDTO.from(savedItem)
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#cartId)")
    @Transactional
    override fun updateItemQuantity(cartId: UUID?, itemId: UUID?, cmd: UpdateItemQuantityCommandDTO): ProductShoppingCartResultDTO {
        val cart = loadCart(cartId)
        val lineItemId = requireItemInCart(cart, itemId)

        val updated = cart.updateItemQuantity(lineItemId, cmd.quantity)
        repository.save(cart)

        return ProductShoppingCartResultDTO.from(updated)
    }

    @PreAuthorize("hasRole('ADMIN') or @ownership.isCartOwner(#cartId)")
    @Transactional
    override fun removeItem(cartId: UUID?, itemId: UUID?) {
        val cart = loadCart(cartId)
        val lineItemId = requireItemInCart(cart, itemId)

        cart.removeItem(lineItemId)
        repository.save(cart)
    }

    /**
     * See design: Product Price-Change Propagation. Loads only OPEN carts
     * containing the product (filtered at the JPQL level), applies the price
     * update to each, and saves with optimistic-lock retry - up to
     * [MAX_OPTIMISTIC_LOCK_RETRIES] attempts per cart. A cart that
     * still conflicts after all retries is logged at WARN and skipped (never
     * crashes the listener thread, never blocks other carts).
     *
     * This method is only a read-only transaction so the lazily loaded line
     * items of the affected carts can be mapped to the domain. It never writes:
     * each update attempt runs in its own fresh `REQUIRES_NEW` transaction via
     * [CartPriceUpdateAttempt], so a failed flush on one attempt never
     * poisons the persistence context of the next attempt (see that class's
     * KDoc for why).
     *
     * A `null` product id is ignored (no-op): there is nothing to update.
     */
    @Transactional(readOnly = true)
    override fun handleProductPriceChanged(productId: UUID?, newPrice: BigDecimal?, version: Long) {
        if (productId == null) {
            log.warn("Ignoring price change without a product id")
            return
        }
        val id = ProductId.of(productId)
        val affectedCarts = repository.findOpenCartsContainingProduct(id)

        for (cart in affectedCarts) {
            applyPriceChangeWithRetry(cart.id!!, id, newPrice, version)
        }
    }

    private fun applyPriceChangeWithRetry(cartId: ShoppingCartId, productId: ProductId, newPrice: BigDecimal?, version: Long) {
        for (attempt in 1..MAX_OPTIMISTIC_LOCK_RETRIES) {
            try {
                priceUpdateAttempt.apply(cartId, productId, newPrice, version)
                return
            } catch (ex: OptimisticLockingFailureException) {
                log.debug("Optimistic lock conflict updating price for cart {} (attempt {}/{})", cartId.value, attempt, MAX_OPTIMISTIC_LOCK_RETRIES)
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    log.warn("Giving up on price update for cart {} after {} optimistic-lock retries", cartId.value, MAX_OPTIMISTIC_LOCK_RETRIES)
                }
            }
        }
    }

    @Transactional
    override fun handleUserDeleted(userId: UUID?) {
        // TODO: decide on archive vs. soft-delete strategy for this user's carts
        // (see design: Database-per-Service / user.deleted handling). Minimal
        // (log-and-skip) handling is acceptable for initial scope.
        log.info("Received user.deleted event for userId={} at {} — no-op (archive/soft-delete not yet implemented)", userId, Instant.now())
    }

    private fun loadCart(id: UUID?): ShoppingCart =
        repository.findById(ShoppingCartId.of(id))
            .orElseThrow { ShoppingCartNotFoundException("Shopping cart not found") }

    private fun requireItemInCart(cart: ShoppingCart, itemId: UUID?): ProductShoppingCartId {
        val lineItemId = ProductShoppingCartId.of(itemId)
        val exists = cart.items!!.any { it.id == lineItemId }
        if (!exists) {
            throw ProductShoppingCartNotFoundException("Item not found in cart with id: $itemId")
        }
        return lineItemId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ShoppingCartServiceImpl::class.java)
        private const val MAX_OPTIMISTIC_LOCK_RETRIES = 3
    }
}
