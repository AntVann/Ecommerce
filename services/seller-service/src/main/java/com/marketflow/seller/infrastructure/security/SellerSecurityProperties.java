package com.marketflow.seller.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.seller")
public record SellerSecurityProperties(
        String identityBaseUrl,
        String identityIssuer,
        String identityAudience,
        String internalServiceKey) {}
