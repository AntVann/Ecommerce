package com.marketflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketflow.notification.infrastructure.provider.FakeEmailProperties;
import com.marketflow.notification.infrastructure.provider.FakeEmailProvider;
import org.junit.jupiter.api.Test;

class FakeEmailProviderTest {
    @Test
    void transientScenarioFailsOnlyConfiguredNumberOfTimes() {
        FakeEmailProvider provider =
                new FakeEmailProvider(new FakeEmailProperties("transient-failure", 2));
        assertThat(
                        provider.send("redacted@example.invalid", "order-confirmation", 1, "order")
                                .retryable())
                .isTrue();
        assertThat(
                        provider.send("redacted@example.invalid", "order-confirmation", 1, "order")
                                .retryable())
                .isTrue();
        assertThat(
                        provider.send("redacted@example.invalid", "order-confirmation", 1, "order")
                                .success())
                .isTrue();
    }

    @Test
    void permanentScenarioDoesNotRetry() {
        FakeEmailProvider provider =
                new FakeEmailProvider(new FakeEmailProperties("permanent-failure", 0));
        var result = provider.send("redacted@example.invalid", "order-confirmation", 1, "order");
        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
    }
}
