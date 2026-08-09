package com.marketflow.payment.infrastructure.messaging;

import com.marketflow.payment.application.PaymentModels.AuthorizationCommand;
import com.marketflow.payment.application.PaymentService;
import com.marketflow.payment.application.PaymentStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentCommandConsumer {
    private static final String CONSUMER = "payment-order-command-v1";
    private final PaymentStore store;
    private final PaymentService service;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PaymentCommandConsumer(
            PaymentStore store, PaymentService service, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.service = service;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "marketflow.order.events.v1", groupId = CONSUMER)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"order.payment-authorization-requested.v1".equals(root.path("eventType").asText())) {
            return;
        }
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        if (!store.claimMessage(CONSUMER, eventId, clock.instant())) {
            return;
        }
        JsonNode data = root.path("data");
        service.authorize(
                new AuthorizationCommand(
                        UUID.fromString(data.path("orderId").asText()),
                        UUID.fromString(data.path("customerId").asText()),
                        new BigDecimal(data.path("amount").asText()),
                        data.path("currency").asText(),
                        data.path("fakePaymentToken").asText()),
                data.path("idempotencyKey").asText(),
                root.path("correlationId").asText(eventId.toString()));
    }
}
