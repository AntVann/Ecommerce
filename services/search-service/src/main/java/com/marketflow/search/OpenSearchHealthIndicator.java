package com.marketflow.search;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("openSearch")
public final class OpenSearchHealthIndicator implements HealthIndicator {
    private final RestClient client;

    public OpenSearchHealthIndicator(RestClient.Builder builder, SearchProperties properties) {
        client = builder.baseUrl(properties.openSearchUrl()).build();
    }

    @Override
    public Health health() {
        try {
            client.get().uri("/_cluster/health").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("reason", "OpenSearch unavailable").build();
        }
    }
}
