package com.marketflow.order.application;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.CheckoutModels.Availability;
import com.marketflow.order.application.CheckoutModels.CartLine;
import com.marketflow.order.application.CheckoutModels.CartSnapshot;
import com.marketflow.order.application.CheckoutModels.CatalogLine;
import com.marketflow.order.application.CheckoutModels.CheckoutCommand;
import com.marketflow.order.application.CheckoutModels.OrderItem;
import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.infrastructure.security.OrderProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CheckoutService {
    private static final String OP = "CREATE_CHECKOUT";
    private final OrderRepository repository;
    private final CheckoutGateways gateways;
    private final ObjectMapper mapper;
    private final OrderProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;

    @Autowired
    public CheckoutService(
            OrderRepository repository,
            CheckoutGateways gateways,
            ObjectMapper mapper,
            OrderProperties properties,
            MeterRegistry metrics) {
        this(repository, gateways, mapper, properties, metrics, Clock.systemUTC());
    }

    CheckoutService(
            OrderRepository r,
            CheckoutGateways g,
            ObjectMapper m,
            OrderProperties p,
            MeterRegistry metrics,
            Clock clock) {
        this.repository = r;
        this.gateways = g;
        this.mapper = m;
        this.properties = p;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public OrderView checkout(
            UUID customer, String key, CheckoutCommand command, String correlation) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{16,128}"))
            bad(
                    "IDEMPOTENCY_KEY_INVALID_400",
                    "Idempotency-Key must contain 16 to 128 safe characters.");
        if (command.cartId() == null
                || command.cartVersion() < 1
                || command.shippingAddress() == null
                || command.billingAddress() == null)
            bad("REQUEST_VALIDATION_400", "Checkout request is incomplete.");
        String hash = hash(command);
        var replay = repository.idempotency(customer, OP, key);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(hash))
                conflict(
                        "IDEMPOTENCY_KEY_REUSED_409",
                        "The idempotency key was already used with different input.");
            if (replay.get().orderId() != null)
                return repository.owned(replay.get().orderId(), customer).orElseThrow();
        }
        gateways.requireActiveCustomer(customer);
        CartSnapshot cart = gateways.cart(customer, command.cartId(), command.cartVersion());
        if (!command.cartId().equals(cart.cartId()) || command.cartVersion() != cart.version())
            conflict("CART_VERSION_CONFLICT_409", "The cart changed before checkout.");
        if (cart.items() == null || cart.items().isEmpty())
            bad("CART_EMPTY_422", "The cart is empty.");
        if (cart.items().size() > 100
                || cart.items().stream().anyMatch(i -> i.quantity() < 1 || i.quantity() > 99))
            bad("CART_QUANTITY_INVALID_422", "A cart quantity is outside the supported range.");
        List<CatalogLine> facts = gateways.catalog(cart.items());
        Map<UUID, CatalogLine> byVariant =
                facts == null
                        ? Map.of()
                        : facts.stream()
                                .collect(
                                        Collectors.toMap(
                                                CatalogLine::variantId,
                                                Function.identity(),
                                                (a, b) -> a));
        if (cart.items().stream()
                .anyMatch(
                        i ->
                                !byVariant.containsKey(i.variantId())
                                        || !byVariant.get(i.variantId()).valid()))
            conflict("CATALOG_REVALIDATION_409", "A product or variant is no longer available.");
        Set<String> currencies =
                facts.stream().map(CatalogLine::currency).collect(Collectors.toSet());
        if (currencies.size() != 1)
            bad("MULTI_CURRENCY_UNSUPPORTED_422", "All order lines must use one currency.");
        gateways.requireApprovedSellers(
                facts.stream().map(CatalogLine::sellerId).distinct().toList());
        Map<UUID, Integer> available =
                gateways.availability(cart.items()).stream()
                        .collect(
                                Collectors.toMap(
                                        Availability::variantId,
                                        Availability::available,
                                        (a, b) -> a));
        if (cart.items().stream()
                .anyMatch(i -> available.getOrDefault(i.variantId(), -1) < i.quantity()))
            conflict("INVENTORY_INSUFFICIENT_409", "Inventory is no longer sufficient.");
        repository.lockCart(customer, command.cartId(), command.cartVersion());
        var existing = repository.cartOrder(customer, command.cartId(), command.cartVersion());
        if (existing.isPresent()) return existing.get();
        if (!repository.claim(customer, OP, key, hash, Instant.now(clock))) {
            var found = repository.idempotency(customer, OP, key).orElseThrow();
            if (!found.requestHash().equals(hash))
                conflict(
                        "IDEMPOTENCY_KEY_REUSED_409",
                        "The idempotency key was already used with different input.");
            return repository.owned(found.orderId(), customer).orElseThrow();
        }
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        String currency = currencies.iterator().next();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(4);
        for (CartLine line : cart.items()) {
            CatalogLine f = byVariant.get(line.variantId());
            BigDecimal unit = money(f.priceAmount());
            BigDecimal total =
                    unit.multiply(BigDecimal.valueOf(line.quantity()))
                            .setScale(4, RoundingMode.UNNECESSARY);
            subtotal = subtotal.add(total);
            items.add(
                    new OrderItem(
                            UUID.randomUUID(),
                            f.sellerId(),
                            f.productId(),
                            f.variantId(),
                            f.productName(),
                            f.variantName(),
                            f.sku(),
                            line.quantity(),
                            unit,
                            currency,
                            total,
                            f.catalogVersion()));
        }
        repository.create(
                orderId,
                customer,
                command,
                currency,
                subtotal,
                command.shippingAddress(),
                command.billingAddress(),
                now);
        items.forEach(i -> repository.item(orderId, i));
        repository.history(orderId, null, "PENDING", null, correlation, now);
        repository.startSaga(
                orderId, items.size(), now.plusSeconds(properties.reservationTtlSeconds()), now);
        OrderView order = repository.order(orderId).orElseThrow();
        repository.orderCreatedOutbox(order, properties.reservationTtlSeconds(), correlation, now);
        repository.complete(customer, OP, key, orderId, 202, order);
        metrics.counter("checkout_created_total").increment();
        return order;
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID customer, UUID order) {
        return repository
                .owned(order, customer)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "ORDER_NOT_FOUND_404",
                                        "Order was not found."));
    }

    private BigDecimal money(BigDecimal value) {
        try {
            if (value == null || value.signum() < 0) throw new ArithmeticException();
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            bad("PRICE_INVALID_422", "A current catalog price is invalid.");
            return null;
        }
    }

    private String hash(CheckoutCommand c) {
        try {
            byte[] encoded = mapper.writeValueAsBytes(c);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash checkout request", e);
        }
    }

    private static void bad(String c, String m) {
        throw new ApiException(
                c.endsWith("422") ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST, c, m);
    }

    private static void conflict(String c, String m) {
        throw new ApiException(HttpStatus.CONFLICT, c, m);
    }
}
