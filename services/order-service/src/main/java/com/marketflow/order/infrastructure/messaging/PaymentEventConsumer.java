package com.marketflow.order.infrastructure.messaging;

import com.marketflow.order.application.OrderRepository;
import com.marketflow.order.application.OrderSagaGateways;
import com.marketflow.order.infrastructure.security.PaymentIntegrationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class PaymentEventConsumer {
    static final String CONSUMER = "order-payment-v1";
    private final OrderRepository repository;
    private final OrderSagaGateways gateways;
    private final PaymentIntegrationProperties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public PaymentEventConsumer(
            OrderRepository repository,
            OrderSagaGateways gateways,
            PaymentIntegrationProperties properties,
            ObjectMapper mapper,
            MeterRegistry metrics) {
        this.repository = repository;
        this.gateways = gateways;
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "marketflow.payment.events.v1", groupId = CONSUMER)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.path("eventType").asText();
        if (!List.of(
                        "payment.payment-authorized.v1",
                        "payment.payment-declined.v1",
                        "payment.payment-failed.v1",
                        "payment.payment-unknown.v1")
                .contains(type)) return;
        UUID eventId = uuid(root, "eventId");
        if (eventId == null || repository.processed(CONSUMER, eventId)) return;
        JsonNode data = root.path("data");
        UUID orderId = uuid(data, "orderId");
        UUID paymentId = uuid(data, "paymentId");
        if (orderId == null || paymentId == null) return;
        var order = repository.order(orderId);
        if (order.isEmpty()) return;
        String correlation = root.path("correlationId").asText("payment-event");
        Instant now = Instant.now();
        BigDecimal amount = decimal(data.path("amount").asText(null));
        String currency = data.path("currency").asText(null);
        if (amount == null
                || amount.compareTo(order.get().grandTotal()) != 0
                || !order.get().currency().equals(currency)) {
            manualReview(orderId, order.get().status(), "PAYMENT_FACT_MISMATCH", correlation, now);
            return;
        }
        if (type.endsWith("authorized.v1")) {
            authorized(orderId, paymentId, order.get().status(), correlation, now);
        } else if (type.endsWith("declined.v1") || type.endsWith("failed.v1")) {
            declined(
                    orderId,
                    paymentId,
                    order.get().status(),
                    data.path("reasonCode")
                            .asText(
                                    type.endsWith("failed.v1")
                                            ? "PAYMENT_FAILED"
                                            : "PAYMENT_DECLINED"),
                    type.endsWith("failed.v1"),
                    correlation,
                    now);
        } else {
            unknown(orderId, paymentId, order.get().status(), correlation, now);
        }
        metrics.counter("order_payment_event_total", "event", type).increment();
    }

    private void authorized(
            UUID order, UUID payment, String status, String correlation, Instant now) {
        if ("CONFIRMED".equals(status)) return;
        if ("PAYMENT_FAILED".equals(status) || "CANCELLED".equals(status)) {
            manualReview(order, status, "AUTHORIZED_AFTER_TERMINAL_FAILURE", correlation, now);
            return;
        }
        if (!"PAYMENT_PROCESSING".equals(status)) {
            manualReview(order, status, "PAYMENT_AUTHORIZED_OUT_OF_SEQUENCE", correlation, now);
            return;
        }
        gateways.confirmInventory(order);
        repository.paymentState(order, payment, "AUTHORIZED", now);
        if (repository.transition(
                order, "PAYMENT_PROCESSING", "CONFIRMED", null, correlation, now)) {
            var changed = repository.order(order).orElseThrow();
            repository.stateOutbox("order.order-confirmed.v1", changed, correlation, now);
        }
    }

    private void declined(
            UUID order,
            UUID payment,
            String status,
            String reason,
            boolean failed,
            String correlation,
            Instant now) {
        if ("PAYMENT_FAILED".equals(status)) return;
        if ("CONFIRMED".equals(status)) {
            manualReview(order, status, "DECLINE_AFTER_CONFIRMATION", correlation, now);
            return;
        }
        if (!"PAYMENT_PROCESSING".equals(status)) {
            manualReview(order, status, "PAYMENT_DECLINED_OUT_OF_SEQUENCE", correlation, now);
            return;
        }
        gateways.releaseInventory(order);
        repository.paymentState(order, payment, failed ? "FAILED" : "DECLINED", now);
        if (repository.transition(
                order, "PAYMENT_PROCESSING", "PAYMENT_FAILED", reason, correlation, now)) {
            var changed = repository.order(order).orElseThrow();
            repository.stateOutbox("order.order-payment-failed.v1", changed, correlation, now);
        }
    }

    private void unknown(UUID order, UUID payment, String status, String correlation, Instant now) {
        if (!"PAYMENT_PROCESSING".equals(status)) {
            if (!List.of("CONFIRMED", "PAYMENT_FAILED", "MANUAL_REVIEW").contains(status))
                manualReview(order, status, "PAYMENT_UNKNOWN_OUT_OF_SEQUENCE", correlation, now);
            return;
        }
        repository.paymentState(order, payment, "UNKNOWN", now);
        repository.sagaState(
                order,
                List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN"),
                "PAYMENT_UNKNOWN",
                now.plusSeconds(properties.unknownTimeoutSeconds()),
                now);
    }

    private void manualReview(
            UUID order, String status, String reason, String correlation, Instant now) {
        repository.transition(order, status, "MANUAL_REVIEW", reason, correlation, now);
    }

    private static UUID uuid(JsonNode node, String name) {
        try {
            return UUID.fromString(node.path(name).asText());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
