package com.marketflow.seller.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("identityDependency")
public final class IdentityDependencyHealthIndicator implements HealthIndicator {

    private final RestClient identity;

    public IdentityDependencyHealthIndicator(RestClient identityRestClient) {
        this.identity = identityRestClient;
    }

    @Override
    public Health health() {
        try {
            identity.get().uri("/actuator/health/liveness").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (RestClientException exception) {
            return Health.down().withDetail("reason", "identity-unavailable").build();
        }
    }
}
