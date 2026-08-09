package com.marketflow.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        long version,
        boolean manualReview,
        Instant createdAt,
        Instant updatedAt) {

    public Payment {
        if (id == null || orderId == null || customerId == null) {
            throw new IllegalArgumentException("Payment identifiers are required");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive with at most 4 decimals");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 code");
        }
    }

    public static Payment create(
            UUID id,
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            Instant now) {
        return new Payment(
                id,
                orderId,
                customerId,
                amount,
                currency,
                PaymentStatus.CREATED,
                0,
                false,
                now,
                now);
    }

    public Payment processing(Instant now) {
        return transition(PaymentStatus.PROCESSING, now);
    }

    public Payment providerOutcome(PaymentStatus outcome, Instant now) {
        if (outcome != PaymentStatus.AUTHORIZED
                && outcome != PaymentStatus.DECLINED
                && outcome != PaymentStatus.FAILED
                && outcome != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("PAYMENT_INVALID_PROVIDER_OUTCOME");
        }
        return transition(outcome, now);
    }

    public Payment reconcile(PaymentStatus outcome, Instant now) {
        if (status != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("PAYMENT_RECONCILIATION_NOT_REQUIRED");
        }
        if (outcome == PaymentStatus.UNKNOWN) {
            return this;
        }
        return transition(outcome, now);
    }

    private Payment transition(PaymentStatus next, Instant now) {
        boolean allowed =
                (status == PaymentStatus.CREATED && next == PaymentStatus.PROCESSING)
                        || (status == PaymentStatus.PROCESSING
                                && (next == PaymentStatus.AUTHORIZED
                                        || next == PaymentStatus.DECLINED
                                        || next == PaymentStatus.FAILED
                                        || next == PaymentStatus.UNKNOWN))
                        || (status == PaymentStatus.UNKNOWN
                                && (next == PaymentStatus.AUTHORIZED
                                        || next == PaymentStatus.DECLINED
                                        || next == PaymentStatus.FAILED));
        if (!allowed) {
            throw new IllegalStateException(
                    "PAYMENT_INVALID_STATE_TRANSITION:" + status + "->" + next);
        }
        return new Payment(
                id,
                orderId,
                customerId,
                amount,
                currency,
                next,
                version + 1,
                manualReview,
                createdAt,
                now);
    }
}
