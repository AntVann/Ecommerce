package com.marketflow.order.application;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.CheckoutModels.OrderView;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentAuthorizationService {
    private final OrderRepository repository;
    private final OrderSagaGateways gateways;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Autowired
    public PaymentAuthorizationService(
            OrderRepository repository,
            OrderSagaGateways gateways,
            MeterRegistry metrics,
            PlatformTransactionManager transactionManager) {
        this(
                repository,
                gateways,
                metrics,
                Clock.systemUTC(),
                new TransactionTemplate(transactionManager));
    }

    PaymentAuthorizationService(
            OrderRepository repository,
            OrderSagaGateways gateways,
            MeterRegistry metrics,
            Clock clock,
            TransactionTemplate transactions) {
        this.repository = repository;
        this.gateways = gateways;
        this.metrics = metrics;
        this.clock = clock;
        this.transactions = transactions;
    }

    public OrderView authorize(
            UUID customer,
            UUID orderId,
            String idempotencyKey,
            String fakePaymentToken,
            String correlation) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{16,128}"))
            bad("IDEMPOTENCY_KEY_INVALID_400", "A valid Idempotency-Key is required.");
        if (fakePaymentToken == null || !fakePaymentToken.matches("mf_fake_[a-z0-9_]{1,96}"))
            bad("FAKE_PAYMENT_TOKEN_INVALID_400", "Only opaque fake payment tokens are accepted.");
        String hash = hash(fakePaymentToken);
        OrderView order =
                transactions.execute(
                        status -> prepare(customer, orderId, idempotencyKey, hash, correlation));
        if (order == null) throw new IllegalStateException("Payment preparation returned no order");
        if (!"PAYMENT_PROCESSING".equals(order.status())) return order;
        metrics.counter("order_payment_authorization_requested_total").increment();
        gateways.authorizePayment(
                order.id(),
                customer,
                order.grandTotal(),
                order.currency(),
                fakePaymentToken,
                idempotencyKey);
        return repository.order(orderId).orElseThrow();
    }

    private OrderView prepare(
            UUID customer, UUID orderId, String idempotencyKey, String hash, String correlation) {
        OrderView order = repository.owned(orderId, customer).orElseThrow(this::notFound);
        var prior = repository.paymentInitiation(orderId);
        if (prior.isPresent()) {
            if (!prior.get().customerId().equals(customer)
                    || !prior.get().idempotencyKey().equals(idempotencyKey)
                    || !prior.get().requestHash().equals(hash))
                conflict(
                        "PAYMENT_INITIATION_CONFLICT_409",
                        "Payment authorization was already initiated with different input.");
            if ("UNKNOWN".equals(order.paymentState())) return order;
            if (!List.of("PAYMENT_PROCESSING", "INVENTORY_RESERVED").contains(order.status()))
                return order;
        } else {
            if (!"INVENTORY_RESERVED".equals(order.status()))
                conflict(
                        "ORDER_NOT_READY_FOR_PAYMENT_409",
                        "Inventory must be reserved before payment authorization.");
            Instant now = Instant.now(clock);
            if (!repository.claimPayment(orderId, customer, idempotencyKey, hash, now))
                conflict(
                        "PAYMENT_INITIATION_CONFLICT_409", "Payment authorization already exists.");
            if (!repository.transition(
                    orderId, "INVENTORY_RESERVED", "PAYMENT_PROCESSING", null, correlation, now))
                conflict("ORDER_STATE_CONFLICT_409", "Order state changed before payment.");
            repository.paymentState(orderId, null, "PROCESSING", now);
            order = repository.order(orderId).orElseThrow();
        }
        return order;
    }

    private String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND_404", "Order was not found.");
    }

    private static void bad(String code, String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static void conflict(String code, String message) {
        throw new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
