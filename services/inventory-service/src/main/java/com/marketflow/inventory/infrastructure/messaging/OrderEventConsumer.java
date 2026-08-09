package com.marketflow.inventory.infrastructure.messaging;

import com.marketflow.inventory.api.ApiException;
import com.marketflow.inventory.application.InventoryService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class OrderEventConsumer {
    private final InventoryService inventory;
    private final ObjectMapper mapper;

    public OrderEventConsumer(InventoryService inventory, ObjectMapper mapper) {
        this.inventory = inventory;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "marketflow.order.events.v1", groupId = "inventory-order-v1")
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.path("eventType").asText();
        if (!"order.order-created.v1".equals(type)
                && !"order.inventory-confirmation-requested.v1".equals(type)
                && !"order.inventory-release-requested.v1".equals(type)) return;
        UUID eventId = UUID.fromString(root.get("eventId").asText());
        UUID orderId = UUID.fromString(root.get("aggregateId").asText());
        String correlationId = root.path("correlationId").asText("unknown");
        JsonNode data = root.get("data");
        UUID dataOrderId = UUID.fromString(data.path("orderId").asText());
        UUID referenceId = UUID.fromString(data.path("referenceId").asText(dataOrderId.toString()));
        if (!orderId.equals(dataOrderId) || !orderId.equals(referenceId)) {
            throw new IllegalArgumentException(
                    "Order event aggregateId, data.orderId, and data.referenceId must match");
        }
        if ("order.inventory-confirmation-requested.v1".equals(type)) {
            inventory.confirmOrderReservation(eventId, referenceId, correlationId);
            return;
        }
        if ("order.inventory-release-requested.v1".equals(type)) {
            inventory.releaseOrderReservation(
                    eventId,
                    referenceId,
                    data.path("reasonCode").asText("PAYMENT_FAILED"),
                    correlationId);
            return;
        }
        var lines = new ArrayList<InventoryService.ReserveLine>();
        for (JsonNode line : data.withArray("lines")) {
            lines.add(
                    new InventoryService.ReserveLine(
                            UUID.fromString(line.get("variantId").asText()),
                            line.get("quantity").asInt()));
        }
        try {
            inventory.reserveOrderEvent(
                    eventId,
                    orderId,
                    lines,
                    Duration.ofSeconds(data.get("reservationTtlSeconds").asLong()),
                    correlationId);
        } catch (ApiException exception) {
            if (!"INVENTORY_INSUFFICIENT_409".equals(exception.code())) throw exception;
            inventory.recordOrderReservationFailure(
                    eventId, orderId, "INSUFFICIENT_AVAILABLE", correlationId);
        }
    }
}
