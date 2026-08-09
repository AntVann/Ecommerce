package com.marketflow.payment.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketflow.payment.application.PaymentProvider.ProviderCommand;
import com.marketflow.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTest {
    private final FakePaymentProvider provider =
            new FakePaymentProvider(
                    new FakeProviderProperties(Duration.ofMillis(5), 2, "test-signature", true));

    @Test
    void supportsImmediateAndAmbiguousOutcomes() {
        assertThat(authorize("approve", "mf_fake_approve").outcome())
                .isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(authorize("decline", "mf_fake_decline").outcome())
                .isEqualTo(PaymentStatus.DECLINED);
        assertThat(authorize("timeout", "mf_fake_timeout").outcome())
                .isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void duplicateScenarioUsesOneProviderEventForDeduplication() {
        var result = authorize("duplicate", "mf_fake_duplicate");

        assertThat(result.outcome()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(result.callbacks()).hasSize(2);
        assertThat(result.callbacks())
                .extracting(callback -> callback.providerEventId())
                .containsOnly(result.callbacks().getFirst().providerEventId());
    }

    @Test
    void providerIdempotencyPreventsSecondAuthorization() {
        var first = authorize("same-key", "mf_fake_approve");
        var second = authorize("same-key", "mf_fake_decline");

        assertThat(second.outcome()).isEqualTo(first.outcome());
        assertThat(second.providerReference()).isEqualTo(first.providerReference());
    }

    private com.marketflow.payment.application.PaymentProvider.ProviderResult authorize(
            String key, String token) {
        return provider.authorize(
                new ProviderCommand(UUID.randomUUID(), key, new BigDecimal("12.00"), "USD", token));
    }
}
