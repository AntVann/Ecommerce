package com.marketflow.cart.application;

import com.marketflow.cart.api.ApiException;
import com.marketflow.cart.domain.Cart;
import com.marketflow.cart.domain.Cart.ActorType;
import com.marketflow.cart.domain.Cart.CartItem;
import com.marketflow.cart.domain.Cart.ValidityStatus;
import com.marketflow.cart.infrastructure.CartProperties;
import com.marketflow.cart.infrastructure.RedisCartRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class CartService {
    private static final int MAX_QUANTITY = 99;
    private static final int MAX_RETRIES = 8;

    private final RedisCartRepository repository;
    private final CatalogGateway catalog;
    private final IdentityGateway identity;
    private final CartProperties properties;
    private final Clock clock;
    private final Counter mutations;
    private final Counter conflicts;

    @Autowired
    public CartService(
            RedisCartRepository repository,
            CatalogGateway catalog,
            IdentityGateway identity,
            CartProperties properties,
            MeterRegistry registry) {
        this(repository, catalog, identity, properties, Clock.systemUTC(), registry);
    }

    CartService(
            RedisCartRepository repository,
            CatalogGateway catalog,
            IdentityGateway identity,
            CartProperties properties,
            Clock clock,
            MeterRegistry registry) {
        this.repository = repository;
        this.catalog = catalog;
        this.identity = identity;
        this.properties = properties;
        this.clock = clock;
        this.mutations = registry.counter("marketflow.cart.mutations");
        this.conflicts = registry.counter("marketflow.cart.conflicts");
    }

    public Cart get(Actor actor) {
        String key = key(actor);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            var found = repository.find(key);
            if (found.isPresent()) return found.get();
            Cart created = empty(actor);
            if (repository.create(key, created, ttl(actor.type()))) return created;
        }
        throw unavailable();
    }

    public Cart add(Actor actor, UUID variantId, int quantity) {
        requireQuantity(quantity);
        CatalogGateway.VariantQuote quote;
        try {
            quote = catalog.quote(variantId);
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CATALOG_VALIDATION_UNAVAILABLE_503",
                    "Catalog validation is temporarily unavailable.");
        }
        if (!quote.active() || quote.productId() == null || quote.amount() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CART_VARIANT_INVALID_422",
                    "The selected variant cannot be added.");
        }
        return mutate(
                actor,
                cart -> {
                    Map<UUID, CartItem> items = new HashMap<>(cart.items());
                    CartItem current = items.get(variantId);
                    if (current == null && items.size() >= properties.maxLines()) {
                        throw new ApiException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "CART_LINE_LIMIT_422",
                                "The cart line limit has been reached.");
                    }
                    int combined =
                            current == null
                                    ? quantity
                                    : Math.min(MAX_QUANTITY, current.quantity() + quantity);
                    items.put(
                            variantId,
                            new CartItem(
                                    quote.productId(),
                                    variantId,
                                    combined,
                                    quote.amount(),
                                    quote.currency(),
                                    clock.instant(),
                                    ValidityStatus.VALID,
                                    null));
                    return next(cart, items);
                });
    }

    public Cart update(Actor actor, UUID variantId, int quantity, long expectedVersion) {
        requireQuantity(quantity);
        return mutate(
                actor,
                expectedVersion,
                cart -> {
                    if (!cart.items().containsKey(variantId)) {
                        throw new ApiException(
                                HttpStatus.NOT_FOUND,
                                "CART_ITEM_NOT_FOUND_404",
                                "Cart item not found.");
                    }
                    Map<UUID, CartItem> items = new HashMap<>(cart.items());
                    CartItem old = items.get(variantId);
                    items.put(
                            variantId,
                            new CartItem(
                                    old.productId(),
                                    old.variantId(),
                                    quantity,
                                    old.estimatedUnitPrice(),
                                    old.currency(),
                                    old.estimatedAt(),
                                    old.validityStatus(),
                                    old.validityReason()));
                    return next(cart, items);
                });
    }

    public Cart remove(Actor actor, UUID variantId, long expectedVersion) {
        return mutate(
                actor,
                expectedVersion,
                cart -> {
                    Map<UUID, CartItem> items = new HashMap<>(cart.items());
                    items.remove(variantId);
                    return next(cart, items);
                });
    }

    public Cart clear(Actor actor, long expectedVersion) {
        return mutate(actor, expectedVersion, cart -> next(cart, Map.of()));
    }

    public Cart merge(String guestDigest, UUID customerId) {
        if (!identity.activeCustomer(customerId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "CUSTOMER_DISABLED_403",
                    "The customer account is not active.");
        }
        Actor guest = new Actor(ActorType.GUEST, guestDigest);
        Actor customer = new Actor(ActorType.CUSTOMER, customerId.toString());
        String guestKey = key(guest);
        String customerKey = key(customer);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            var guestCart = repository.find(guestKey);
            if (guestCart.isEmpty()) return get(customer);
            var customerCart = repository.find(customerKey);
            Map<UUID, CartItem> items = new HashMap<>();
            guestCart.get().items().values().forEach(item -> items.put(item.variantId(), item));
            customerCart.ifPresent(
                    cart ->
                            cart.items()
                                    .values()
                                    .forEach(
                                            item -> {
                                                CartItem guestItem = items.get(item.variantId());
                                                int quantity =
                                                        guestItem == null
                                                                ? item.quantity()
                                                                : Math.min(
                                                                        MAX_QUANTITY,
                                                                        item.quantity()
                                                                                + guestItem
                                                                                        .quantity());
                                                items.put(
                                                        item.variantId(),
                                                        new CartItem(
                                                                item.productId(),
                                                                item.variantId(),
                                                                quantity,
                                                                item.estimatedUnitPrice(),
                                                                item.currency(),
                                                                item.estimatedAt(),
                                                                item.validityStatus(),
                                                                item.validityReason()));
                                            }));
            if (items.size() > properties.maxLines()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "CART_LINE_LIMIT_422",
                        "Merged cart exceeds the line limit.");
            }
            Instant now = clock.instant();
            Cart merged =
                    new Cart(
                            customerCart.map(Cart::cartId).orElseGet(UUID::randomUUID),
                            ActorType.CUSTOMER,
                            customer.actorKey(),
                            customerCart.map(Cart::version).orElse(0L) + 1,
                            items,
                            customerCart.map(Cart::createdAt).orElse(now),
                            now,
                            now.plus(properties.customerTtl()));
            if (repository.merge(
                    guestKey,
                    guestCart.get().version(),
                    customerKey,
                    customerCart.map(Cart::version).orElse(-1L),
                    merged,
                    properties.customerTtl())) {
                mutations.increment();
                return merged;
            }
        }
        conflicts.increment();
        throw conflict();
    }

    public Cart checkoutSnapshot(UUID customerId, UUID cartId, long version) {
        if (!identity.activeCustomer(customerId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "CUSTOMER_DISABLED_403",
                    "The customer account is not active.");
        }
        Cart cart =
                repository
                        .find(key(new Actor(ActorType.CUSTOMER, customerId.toString())))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "CART_NOT_FOUND_404",
                                                "Cart not found."));
        if (!cart.cartId().equals(cartId) || cart.version() != version) {
            throw conflict();
        }
        return cart;
    }

    private Cart mutate(Actor actor, UnaryOperator<Cart> operation) {
        return mutate(actor, null, operation);
    }

    private Cart mutate(Actor actor, Long clientVersion, UnaryOperator<Cart> operation) {
        String key = key(actor);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Cart cart = get(actor);
            if (clientVersion != null && cart.version() != clientVersion) throw conflict();
            Cart updated = operation.apply(cart);
            if (repository.replace(key, cart.version(), updated, ttl(actor.type()))) {
                mutations.increment();
                return updated;
            }
        }
        conflicts.increment();
        throw conflict();
    }

    private Cart next(Cart cart, Map<UUID, CartItem> items) {
        Instant now = clock.instant();
        return new Cart(
                cart.cartId(),
                cart.actorType(),
                cart.actorKey(),
                cart.version() + 1,
                items,
                cart.createdAt(),
                now,
                now.plus(ttl(cart.actorType())));
    }

    private Cart empty(Actor actor) {
        Instant now = clock.instant();
        return new Cart(
                UUID.randomUUID(),
                actor.type(),
                actor.actorKey(),
                1,
                Map.of(),
                now,
                now,
                now.plus(ttl(actor.type())));
    }

    private Duration ttl(ActorType type) {
        return type == ActorType.GUEST ? properties.guestTtl() : properties.customerTtl();
    }

    private String key(Actor actor) {
        return properties.namespace()
                + ":"
                + (actor.type() == ActorType.GUEST ? "guest:" : "user:")
                + actor.actorKey();
    }

    private void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CART_QUANTITY_INVALID_400",
                    "Quantity must be between 1 and 99.");
        }
    }

    private ApiException conflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "CART_VERSION_CONFLICT_409",
                "The cart changed; reload and retry.");
    }

    private ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CART_STORAGE_UNAVAILABLE_503",
                "Cart storage is unavailable.");
    }

    public record Actor(ActorType type, String actorKey) {}
}
