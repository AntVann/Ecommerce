package com.marketflow.notification.infrastructure.messaging;

import com.marketflow.notification.application.NotificationModels.CreateCommand;
import com.marketflow.notification.application.NotificationService;
import com.marketflow.notification.application.NotificationStore;
import com.marketflow.notification.domain.NotificationKind;
import java.time.Clock;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationEventConsumer {
    private static final String CONSUMER = "notification-order-events-v1";
    private final NotificationStore store;
    private final NotificationService service;
    private final ObjectMapper mapper;
    private final Clock clock;

    public NotificationEventConsumer(
            NotificationStore store,
            NotificationService service,
            ObjectMapper mapper,
            Clock clock) {
        this.store = store;
        this.service = service;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "marketflow.order.events.v1", groupId = CONSUMER)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.path("eventType").asText();
        NotificationKind kind =
                switch (type) {
                    case "order.order-confirmed.v1" -> NotificationKind.ORDER_CONFIRMATION;
                    case "order.shipment-created.v1", "order.order-shipped.v1" ->
                            NotificationKind.SHIPMENT;
                    default -> null;
                };
        if (kind == null) return;
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        if (!store.claimEvent(CONSUMER, eventId, clock.instant())) return;
        JsonNode data = root.path("data");
        UUID orderId = UUID.fromString(data.path("orderId").asText());
        UUID customerId = UUID.fromString(data.path("customerId").asText());
        String recipient = data.path("customerEmail").asText("customer@example.invalid");
        String vars =
                "{\"orderId\":\""
                        + orderId
                        + "\",\"trackingNumber\":\""
                        + data.path("trackingNumber").asText("")
                        + "\"}";
        service.enqueue(
                new CreateCommand(
                        eventId,
                        customerId,
                        orderId,
                        kind,
                        recipient,
                        kind == NotificationKind.ORDER_CONFIRMATION
                                ? "order-confirmation"
                                : "shipment-created",
                        1,
                        vars));
    }
}
