package com.marketflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {
    private final Instant now = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void validatesStateMachineAndVersion() {
        Payment payment =
                Payment.create(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new BigDecimal("19.99"),
                                "USD",
                                now)
                        .processing(now)
                        .providerOutcome(PaymentStatus.AUTHORIZED, now);

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.version()).isEqualTo(2);
        assertThatThrownBy(() -> payment.providerOutcome(PaymentStatus.DECLINED, now))
                .hasMessageContaining("PAYMENT_INVALID_STATE_TRANSITION");
    }

    @Test
    void unknownCanOnlyBeResolvedThroughReconciliation() {
        Payment payment =
                Payment.create(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new BigDecimal("4.20"),
                                "USD",
                                now)
                        .processing(now)
                        .providerOutcome(PaymentStatus.UNKNOWN, now);

        assertThat(payment.reconcile(PaymentStatus.DECLINED, now).status())
                .isEqualTo(PaymentStatus.DECLINED);
        assertThatThrownBy(() -> payment.processing(now))
                .hasMessageContaining("PAYMENT_INVALID_STATE_TRANSITION");
    }
}
