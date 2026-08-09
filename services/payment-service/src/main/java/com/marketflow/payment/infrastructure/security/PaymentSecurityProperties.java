package com.marketflow.payment.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.payment.security")
public record PaymentSecurityProperties(String internalServiceKey) {
    public PaymentSecurityProperties {
        internalServiceKey =
                internalServiceKey == null
                        ? "local-development-only-change-me"
                        : internalServiceKey;
    }
}
