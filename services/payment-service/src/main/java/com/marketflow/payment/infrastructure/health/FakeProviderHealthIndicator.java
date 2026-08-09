package com.marketflow.payment.infrastructure.health;

import com.marketflow.payment.application.PaymentProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("fakePaymentProviderHealth")
public class FakeProviderHealthIndicator implements HealthIndicator {
    private final PaymentProvider provider;

    public FakeProviderHealthIndicator(PaymentProvider provider) {
        this.provider = provider;
    }

    @Override
    public Health health() {
        return provider.available()
                ? Health.up().withDetail("provider", "simulated").build()
                : Health.down().withDetail("provider", "simulated-unavailable").build();
    }
}
