package com.marketflow.order.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.order.payment")
public record PaymentIntegrationProperties(
        String baseUrl, String internalServiceKey, long unknownTimeoutSeconds) {}
