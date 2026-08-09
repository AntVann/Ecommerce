package com.marketflow.order.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.application.OrderRepository;
import com.marketflow.order.application.OrderSagaGateways;
import com.marketflow.order.infrastructure.security.PaymentIntegrationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentEventConsumerTest {
    private OrderRepository repository;
    private OrderSagaGateways gateways;
    private PaymentEventConsumer consumer;
    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = org.mockito.Mockito.mock(OrderRepository.class);
        gateways = org.mockito.Mockito.mock(OrderSagaGateways.class);
        consumer =
                new PaymentEventConsumer(
                        repository,
                        gateways,
                        new PaymentIntegrationProperties("payment", "key", 900),
                        new ObjectMapper(),
                        new SimpleMeterRegistry());
        OrderView processing = order("PAYMENT_PROCESSING");
        when(repository.order(orderId)).thenReturn(Optional.of(processing));
        when(repository.transition(any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void authorizationConfirmsInventoryAndOrder() throws Exception {
        consumer.consume(event("payment.payment-authorized.v1", null));

        verify(gateways).confirmInventory(orderId);
        verify(repository).paymentState(eq(orderId), eq(paymentId), eq("AUTHORIZED"), any());
        verify(repository)
                .transition(
                        eq(orderId),
                        eq("PAYMENT_PROCESSING"),
                        eq("CONFIRMED"),
                        eq(null),
                        eq("c"),
                        any());
    }

    @Test
    void failedPaymentReleasesInventory() throws Exception {
        consumer.consume(event("payment.payment-failed.v1", "PROVIDER_FAILURE"));

        verify(gateways).releaseInventory(orderId);
        verify(repository).paymentState(eq(orderId), eq(paymentId), eq("FAILED"), any());
        verify(repository)
                .transition(
                        eq(orderId),
                        eq("PAYMENT_PROCESSING"),
                        eq("PAYMENT_FAILED"),
                        eq("PROVIDER_FAILURE"),
                        eq("c"),
                        any());
    }

    @Test
    void unknownPaymentIsHeldForReconciliationWithoutRelease() throws Exception {
        consumer.consume(event("payment.payment-unknown.v1", null));

        verify(repository).paymentState(eq(orderId), eq(paymentId), eq("UNKNOWN"), any());
        verify(repository)
                .sagaState(
                        eq(orderId),
                        eq(java.util.List.of("PAYMENT_PROCESSING", "PAYMENT_UNKNOWN")),
                        eq("PAYMENT_UNKNOWN"),
                        any(),
                        any());
        verify(gateways, never()).releaseInventory(any());
        verify(gateways, never()).confirmInventory(any());
    }

    @Test
    void duplicateEventHasNoSideEffect() throws Exception {
        when(repository.processed(any(), any())).thenReturn(true);
        consumer.consume(event("payment.payment-authorized.v1", null));
        verify(gateways, never()).confirmInventory(any());
    }

    private String event(String type, String reason) {
        String reasonJson = reason == null ? "" : ",\"reasonCode\":\"" + reason + "\"";
        return "{\"eventId\":\""
                + UUID.randomUUID()
                + "\",\"eventType\":\""
                + type
                + "\",\"correlationId\":\"c\",\"data\":{\"orderId\":\""
                + orderId
                + "\",\"paymentId\":\""
                + paymentId
                + "\",\"attemptId\":\""
                + UUID.randomUUID()
                + "\",\"amount\":\"10.0000\",\"currency\":\"USD\""
                + reasonJson
                + "}}";
    }

    private OrderView order(String status) {
        OrderView order = org.mockito.Mockito.mock(OrderView.class);
        when(order.id()).thenReturn(orderId);
        when(order.status()).thenReturn(status);
        when(order.grandTotal()).thenReturn(new BigDecimal("10.0000"));
        when(order.currency()).thenReturn("USD");
        return order;
    }
}
