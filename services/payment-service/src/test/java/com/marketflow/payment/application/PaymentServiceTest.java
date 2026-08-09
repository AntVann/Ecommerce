package com.marketflow.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.payment.application.PaymentModels.AuthorizationCommand;
import com.marketflow.payment.application.PaymentModels.CallbackCommand;
import com.marketflow.payment.application.PaymentModels.PaymentView;
import com.marketflow.payment.application.PaymentProvider.ProviderResult;
import com.marketflow.payment.domain.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentServiceTest {
    private final Instant now = Instant.parse("2026-08-06T00:00:00Z");
    private PaymentStore store;
    private PaymentProvider provider;
    private PaymentService service;
    private PaymentView authorized;

    @BeforeEach
    void setup() {
        store = mock(PaymentStore.class);
        provider = mock(PaymentProvider.class);
        authorized =
                new PaymentView(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("12.00"),
                        "USD",
                        PaymentStatus.AUTHORIZED,
                        UUID.randomUUID(),
                        null,
                        false,
                        2,
                        now);
        service =
                new PaymentService(
                        store,
                        provider,
                        Clock.fixed(now, ZoneOffset.UTC),
                        mock(TaskScheduler.class),
                        new SimpleMeterRegistry(),
                        mock(TransactionTemplate.class));
    }

    @Test
    void identicalRetryReturnsPaymentAndCallsProviderOnce() {
        AuthorizationCommand command = command("mf_fake_approve");
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> hash = new AtomicReference<>();
        when(store.claimIdempotency(anyString(), anyString(), anyString(), any()))
                .thenAnswer(
                        invocation -> {
                            hash.set(invocation.getArgument(2));
                            return claims.getAndIncrement() == 0;
                        });
        when(store.idempotency(anyString(), anyString()))
                .thenAnswer(
                        invocation ->
                                Optional.of(
                                        new PaymentStore.IdempotencyRecord(
                                                hash.get(), authorized.paymentId())));
        when(provider.authorize(any()))
                .thenReturn(
                        new ProviderResult(
                                PaymentStatus.AUTHORIZED,
                                authorized.paymentId().toString(),
                                null,
                                List.of()));
        when(store.payment(any())).thenReturn(Optional.of(authorized));

        assertThat(service.authorize(command, "payment-key-123", "correlation").paymentId())
                .isEqualTo(authorized.paymentId());
        assertThat(service.authorize(command, "payment-key-123", "correlation").paymentId())
                .isEqualTo(authorized.paymentId());

        verify(provider, times(1)).authorize(any());
        verify(store, times(1))
                .outbox(eq(authorized), eq("payment.payment-authorized.v1"), eq(null), anyString());
    }

    @Test
    void timeoutBecomesUnknownWithoutBlindRetry() {
        PaymentView unknown =
                new PaymentView(
                        authorized.paymentId(),
                        authorized.orderId(),
                        authorized.customerId(),
                        authorized.amount(),
                        authorized.currency(),
                        PaymentStatus.UNKNOWN,
                        authorized.attemptId(),
                        "PROVIDER_TIMEOUT",
                        false,
                        2,
                        now);
        when(store.claimIdempotency(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(provider.authorize(any()))
                .thenReturn(
                        new ProviderResult(
                                PaymentStatus.UNKNOWN,
                                authorized.paymentId().toString(),
                                "PROVIDER_TIMEOUT",
                                List.of()));
        when(store.payment(any())).thenReturn(Optional.of(unknown));

        assertThat(service.authorize(command("mf_fake_timeout"), "payment-key-456", "c").status())
                .isEqualTo(PaymentStatus.UNKNOWN);
        verify(provider, times(1)).authorize(any());
        verify(store)
                .transition(
                        any(),
                        eq(PaymentStatus.PROCESSING),
                        eq(PaymentStatus.UNKNOWN),
                        eq("PROVIDER_TIMEOUT"),
                        any());
    }

    @Test
    void duplicateCallbackAppliesStateAndEventExactlyOnce() {
        PaymentView processing =
                new PaymentView(
                        authorized.paymentId(),
                        authorized.orderId(),
                        authorized.customerId(),
                        authorized.amount(),
                        authorized.currency(),
                        PaymentStatus.PROCESSING,
                        authorized.attemptId(),
                        null,
                        false,
                        1,
                        now);
        when(store.byProviderReference("provider-reference"))
                .thenReturn(Optional.of(processing), Optional.of(authorized));
        when(store.recordCallback(any(), anyString(), any(), any(), any())).thenReturn(true, false);
        when(store.transition(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.payment(any())).thenReturn(Optional.of(authorized));

        UUID eventId = UUID.randomUUID();
        CallbackCommand callback =
                new CallbackCommand(eventId, "provider-reference", PaymentStatus.AUTHORIZED, null);

        PaymentView first = service.callback(callback, "c");
        PaymentView duplicate = service.callback(callback, "c");

        assertThat(first).isEqualTo(authorized);
        assertThat(duplicate).isEqualTo(authorized);
        verify(store, times(1)).transition(any(), any(), any(), any(), any());
        verify(store, times(1)).outbox(any(), anyString(), any(), anyString());
        verify(store, times(1)).completeAttemptByReference(anyString(), any(), any(), any());
    }

    @Test
    void unresolvedReconciliationEscalatesToManualReview() {
        PaymentView unknown =
                new PaymentView(
                        authorized.paymentId(),
                        authorized.orderId(),
                        authorized.customerId(),
                        authorized.amount(),
                        authorized.currency(),
                        PaymentStatus.UNKNOWN,
                        authorized.attemptId(),
                        "PROVIDER_TIMEOUT",
                        false,
                        2,
                        now);
        PaymentView review =
                new PaymentView(
                        unknown.paymentId(),
                        unknown.orderId(),
                        unknown.customerId(),
                        unknown.amount(),
                        unknown.currency(),
                        PaymentStatus.UNKNOWN,
                        unknown.attemptId(),
                        "MANUAL_REVIEW_REQUIRED",
                        true,
                        3,
                        now);
        when(store.payment(any()))
                .thenReturn(Optional.of(unknown), Optional.of(review), Optional.of(review));
        when(provider.reconcile(anyString(), anyString()))
                .thenReturn(
                        new ProviderResult(
                                PaymentStatus.UNKNOWN,
                                unknown.paymentId().toString(),
                                "RECONCILIATION_UNRESOLVED",
                                List.of()));
        when(store.reconciliationContext(unknown.paymentId()))
                .thenReturn(
                        Optional.of(
                                new PaymentStore.ReconciliationContext(
                                        "payment-key-789", unknown.paymentId().toString())));
        when(store.incrementReconciliation(any(), any())).thenReturn(3);

        assertThat(service.reconcile(unknown.paymentId(), "payment-key-789", "c").manualReview())
                .isTrue();
        verify(store).requireManualReview(eq(unknown.paymentId()), any());
        verify(store)
                .outbox(
                        eq(review),
                        eq("payment.payment-unknown.v1"),
                        eq("MANUAL_REVIEW_REQUIRED"),
                        eq("c"));
    }

    @Test
    void reconciliationRejectsAKeyOtherThanTheOriginalProviderKey() {
        PaymentView unknown =
                new PaymentView(
                        authorized.paymentId(),
                        authorized.orderId(),
                        authorized.customerId(),
                        authorized.amount(),
                        authorized.currency(),
                        PaymentStatus.UNKNOWN,
                        authorized.attemptId(),
                        "PROVIDER_TIMEOUT",
                        false,
                        2,
                        now);
        when(store.payment(unknown.paymentId())).thenReturn(Optional.of(unknown));
        when(store.reconciliationContext(unknown.paymentId()))
                .thenReturn(
                        Optional.of(
                                new PaymentStore.ReconciliationContext(
                                        "payment-key-original", unknown.paymentId().toString())));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.reconcile(unknown.paymentId(), "payment-key-other", "c"))
                .isInstanceOf(PaymentService.PaymentException.class)
                .hasMessage("PAYMENT_IDEMPOTENCY_KEY_MISMATCH");
        verify(provider, times(0)).reconcile(anyString(), anyString());
    }

    private AuthorizationCommand command(String token) {
        return new AuthorizationCommand(
                authorized.orderId(),
                authorized.customerId(),
                authorized.amount(),
                authorized.currency(),
                token);
    }
}
