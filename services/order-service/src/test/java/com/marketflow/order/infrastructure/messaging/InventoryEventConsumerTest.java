package com.marketflow.order.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.application.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InventoryEventConsumerTest {
    private OrderRepository repository;
    private InventoryEventConsumer consumer;
    private final UUID order = UUID.randomUUID(),
            variant = UUID.randomUUID(),
            event = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(OrderRepository.class);
        consumer =
                new InventoryEventConsumer(
                        repository, new ObjectMapper(), new SimpleMeterRegistry());
        when(repository.processed(any(), any())).thenReturn(false);
        when(repository.order(order)).thenReturn(Optional.of(mock(OrderView.class)));
    }

    @Test
    void lastReservedLineTransitionsExactlyOnce() throws Exception {
        when(repository.sagaForUpdate(order))
                .thenReturn(new OrderRepository.Saga(2, 1, "AWAITING_INVENTORY"));
        when(repository.recordOutcome(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.incrementReserved(any(), any())).thenReturn(2);
        consumer.consume(reserved());
        verify(repository)
                .transition(
                        eq(order), eq("PENDING"), eq("INVENTORY_RESERVED"), isNull(), any(), any());
    }

    @Test
    void duplicateMessageDoesNothing() throws Exception {
        when(repository.processed(any(), eq(event))).thenReturn(true);
        consumer.consume(reserved());
        verify(repository, never()).sagaForUpdate(any());
    }

    @Test
    void reservationFailureCancelsPendingOrder() throws Exception {
        when(repository.sagaForUpdate(order))
                .thenReturn(new OrderRepository.Saga(2, 0, "AWAITING_INVENTORY"));
        consumer.consume(
                "{\"eventId\":\""
                        + event
                        + "\",\"eventType\":\"inventory.inventory-reservation-failed.v1\",\"correlationId\":\"c\",\"data\":{\"referenceId\":\""
                        + order
                        + "\",\"reasonCode\":\"INSUFFICIENT_AVAILABLE\"}}");
        verify(repository)
                .transition(
                        eq(order),
                        eq("PENDING"),
                        eq("CANCELLED"),
                        eq("INSUFFICIENT_AVAILABLE"),
                        eq("c"),
                        any());
    }

    private String reserved() {
        return "{\"eventId\":\""
                + event
                + "\",\"eventType\":\"inventory.inventory-reserved.v1\",\"correlationId\":\"c\",\"data\":{\"variantId\":\""
                + variant
                + "\",\"details\":{\"referenceId\":\""
                + order
                + "\"}}}";
    }
}
