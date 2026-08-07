package com.marketflow.cart.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.cart")
public record CartProperties(
        String identityBaseUrl,
        String identityIssuer,
        String identityAudience,
        String catalogBaseUrl,
        String internalServiceKey,
        String namespace,
        Duration guestTtl,
        Duration customerTtl,
        int maxLines,
        boolean secureCookies) {}
