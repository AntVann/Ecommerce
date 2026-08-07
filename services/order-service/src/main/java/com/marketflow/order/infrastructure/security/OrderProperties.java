package com.marketflow.order.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.order")
public record OrderProperties(
        String identityBaseUrl,
        String identityIssuer,
        String identityAudience,
        String cartBaseUrl,
        String catalogBaseUrl,
        String sellerBaseUrl,
        String inventoryBaseUrl,
        String internalServiceKey,
        long reservationTtlSeconds) {}
