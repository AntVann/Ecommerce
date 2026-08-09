package com.marketflow.order.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.domain.Address;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentAuthorizationServiceTest {
    private OrderRepository repository;
    private OrderSagaGateways gateways;
    private PaymentAuthorizationService service;
    private final UUID customer = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        repository = org.mockito.Mockito.mock(OrderRepository.class);
        gateways = org.mockito.Mockito.mock(OrderSagaGateways.class);
        TransactionTemplate transactions = org.mockito.Mockito.mock(TransactionTemplate.class);
        when(transactions.execute(any()))
                .thenAnswer(
                        invocation ->
                                ((TransactionCallback<OrderView>) invocation.getArgument(0))
                                        .doInTransaction(
                                                org.mockito.Mockito.mock(TransactionStatus.class)));
        service =
                new PaymentAuthorizationService(
                        repository,
                        gateways,
                        new SimpleMeterRegistry(),
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                        transactions);
    }

    @Test
    void persistsProcessingBeforeCallingPaymentAndRetriesSameKey() {
        OrderView reserved = view("INVENTORY_RESERVED");
        OrderView processing = view("PAYMENT_PROCESSING");
        when(repository.owned(orderId, customer)).thenReturn(Optional.of(reserved));
        when(repository.paymentInitiation(orderId)).thenReturn(Optional.empty());
        when(repository.claimPayment(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.transition(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.order(orderId)).thenReturn(Optional.of(processing));

        service.authorize(customer, orderId, "abcdefghijklmnop", "mf_fake_approved", "c");

        verify(repository).paymentState(orderId, null, "PROCESSING", Instant.EPOCH);
        verify(gateways)
                .authorizePayment(
                        orderId,
                        customer,
                        new BigDecimal("10.0000"),
                        "USD",
                        "mf_fake_approved",
                        "abcdefghijklmnop");
    }

    @Test
    void rejectsRealOrUnscopedPaymentTokens() {
        assertThatThrownBy(
                        () ->
                                service.authorize(
                                        customer,
                                        orderId,
                                        "abcdefghijklmnop",
                                        "not-a-fake-payment-token",
                                        "c"))
                .hasMessageContaining("fake payment tokens");
    }

    private OrderView view(String status) {
        Address address = new Address("Buyer", "1 Main", null, "City", null, "1", "US");
        return new OrderView(
                orderId,
                customer,
                UUID.randomUUID(),
                1,
                status,
                null,
                "USD",
                new BigDecimal("10.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.0000"),
                address,
                address,
                2,
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(),
                null,
                null);
    }
}
