package com.marketflow.order.infrastructure.messaging;

import com.marketflow.order.application.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryEventConsumer {
    private static final String CONSUMER = "order-inventory-v1";
    private final OrderRepository repository;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public InventoryEventConsumer(OrderRepository r, ObjectMapper m, MeterRegistry metrics) {
        repository = r;
        mapper = m;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "marketflow.inventory.events.v1", groupId = CONSUMER)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.path("eventType").asText();
        if (!type.equals("inventory.inventory-reserved.v1")
                && !type.equals("inventory.inventory-reservation-failed.v1")
                && !type.equals("inventory.inventory-released.v1")) return;
        UUID event = UUID.fromString(root.path("eventId").asText());
        if (repository.processed(CONSUMER, event)) return;
        JsonNode data = root.path("data");
        UUID order = uuid(data, "referenceId");
        if (order == null) order = uuid(data.path("details"), "referenceId");
        if (order == null || repository.order(order).isEmpty()) return;
        String correlation = root.path("correlationId").asText("inventory-event");
        Instant now = Instant.now();
        if (type.equals("inventory.inventory-reserved.v1")) {
            UUID variant = uuid(data, "variantId");
            if (variant == null) return;
            var saga = repository.sagaForUpdate(order);
            if (!"AWAITING_INVENTORY".equals(saga.state())) {
                if ("CANCELLED".equals(saga.state()))
                    repository.transition(
                            order,
                            "CANCELLED",
                            "MANUAL_REVIEW",
                            "LATE_INVENTORY_RESERVED",
                            correlation,
                            now);
                return;
            }
            if (repository.recordOutcome(order, variant, "RESERVED", event, now)) {
                int count = repository.incrementReserved(order, now);
                if (count == saga.expected())
                    repository.transition(
                            order, "PENDING", "INVENTORY_RESERVED", null, correlation, now);
            }
            return;
        }
        var saga = repository.sagaForUpdate(order);
        String reason =
                type.endsWith("failed.v1")
                        ? data.path("reasonCode").asText("INVENTORY_RESERVATION_FAILED")
                        : data.path("details").path("reasonCode").asText("INVENTORY_RELEASED");
        if ("INVENTORY_RESERVED".equals(saga.state()) && type.endsWith("failed.v1"))
            repository.transition(
                    order,
                    "INVENTORY_RESERVED",
                    "MANUAL_REVIEW",
                    "CONTRADICTORY_INVENTORY_FAILURE",
                    correlation,
                    now);
        else if ("AWAITING_INVENTORY".equals(saga.state()))
            repository.transition(order, "PENDING", "CANCELLED", reason, correlation, now);
        else if ("INVENTORY_RESERVED".equals(saga.state()) && "EXPIRED".equals(reason))
            repository.transition(
                    order,
                    "INVENTORY_RESERVED",
                    "CANCELLED",
                    "RESERVATION_EXPIRED",
                    correlation,
                    now);
        metrics.counter("order_saga_inventory_terminal_total", "event", type).increment();
    }

    private static UUID uuid(JsonNode n, String name) {
        String value = n.path(name).asText(null);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
