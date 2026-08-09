package com.marketflow.payment.application;

import com.marketflow.payment.domain.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentModels {
    private PaymentModels() {}

    public record AuthorizationCommand(
            @NotNull UUID orderId,
            @NotNull UUID customerId,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank
                    @Pattern(
                            regexp =
                                    "mf_fake_(approve|decline|timeout|delayed_approve|delayed_decline|duplicate)")
                    String fakePaymentToken) {}

    public record CallbackCommand(
            @NotNull UUID providerEventId,
            @NotBlank String providerReference,
            @NotNull PaymentStatus status,
            String reasonCode) {}

    public record PaymentView(
            UUID paymentId,
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            UUID attemptId,
            String reasonCode,
            boolean manualReview,
            long version,
            Instant updatedAt) {}
}
