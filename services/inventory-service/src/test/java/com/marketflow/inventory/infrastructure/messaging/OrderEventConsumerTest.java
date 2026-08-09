package com.marketflow.inventory.infrastructure.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.marketflow.inventory.application.InventoryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {
    @Mock InventoryService inventory;

    @Test
    void routesConfirmationRequest() throws Exception {
        UUID event = UUID.randomUUID();
        UUID order = UUID.randomUUID();

        consumer().consume(event(event, order, "order.inventory-confirmation-requested.v1", ""));

        verify(inventory).confirmOrderReservation(event, order, "correlation");
    }

    @Test
    void routesCompensatingReleaseWithReason() throws Exception {
        UUID event = UUID.randomUUID();
        UUID order = UUID.randomUUID();

        consumer()
                .consume(
                        event(
                                event,
                                order,
                                "order.inventory-release-requested.v1",
                                ",\"reasonCode\":\"PAYMENT_DECLINED\""));

        verify(inventory).releaseOrderReservation(event, order, "PAYMENT_DECLINED", "correlation");
    }

    @Test
    void ignoresUnrelatedOrderEvents() throws Exception {
        consumer().consume("{\"eventType\":\"order.order-confirmed.v1\",\"data\":{}}");

        verify(inventory, never())
                .confirmOrderReservation(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private OrderEventConsumer consumer() {
        return new OrderEventConsumer(inventory, new ObjectMapper());
    }

    private static String event(UUID event, UUID order, String type, String extraData) {
        return "{\"eventId\":\""
                + event
                + "\",\"eventType\":\""
                + type
                + "\",\"aggregateId\":\""
                + order
                + "\",\"correlationId\":\"correlation\",\"data\":{\"orderId\":\""
                + order
                + "\",\"referenceId\":\""
                + order
                + "\""
                + extraData
                + "}}";
    }
}
