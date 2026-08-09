package com.marketflow.payment.application;

import com.marketflow.payment.domain.PaymentStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface PaymentProvider {
    ProviderResult authorize(ProviderCommand command);

    ProviderResult reconcile(String idempotencyKey, String providerReference);

    boolean available();

    record ProviderCommand(
            UUID paymentId,
            String idempotencyKey,
            java.math.BigDecimal amount,
            String currency,
            String opaqueToken) {}

    record CallbackPlan(
            UUID providerEventId,
            String providerReference,
            PaymentStatus outcome,
            String reasonCode,
            Duration delay) {}

    record ProviderResult(
            PaymentStatus outcome,
            String providerReference,
            String reasonCode,
            List<CallbackPlan> callbacks) {
        public ProviderResult {
            callbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
        }
    }
}
