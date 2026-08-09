package com.marketflow.payment.application;

import com.marketflow.payment.application.PaymentModels.PaymentView;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStore {
    boolean claimIdempotency(String operation, String key, String requestHash, Instant now);

    Optional<IdempotencyRecord> idempotency(String operation, String key);

    void completeIdempotency(String operation, String key, UUID paymentId);

    void create(Payment payment);

    UUID createAttempt(UUID paymentId, String key, Instant now);

    void providerResult(
            UUID attemptId,
            String providerReference,
            PaymentStatus status,
            String reasonCode,
            Instant now);

    Optional<PaymentView> payment(UUID paymentId);

    Optional<PaymentView> byProviderReference(String providerReference);

    Optional<ReconciliationContext> reconciliationContext(UUID paymentId);

    boolean transition(
            UUID paymentId,
            PaymentStatus expected,
            PaymentStatus next,
            String reasonCode,
            Instant now);

    boolean recordCallback(
            UUID providerEventId,
            String providerReference,
            PaymentStatus outcome,
            String reasonCode,
            Instant now);

    void completeAttemptByReference(
            String providerReference, PaymentStatus outcome, String reasonCode, Instant now);

    void outbox(PaymentView payment, String eventType, String reasonCode, String correlationId);

    int incrementReconciliation(UUID paymentId, Instant now);

    void requireManualReview(UUID paymentId, Instant now);

    boolean claimMessage(String consumer, UUID eventId, Instant now);

    record IdempotencyRecord(String requestHash, UUID paymentId) {}

    record ReconciliationContext(String idempotencyKey, String providerReference) {}
}
