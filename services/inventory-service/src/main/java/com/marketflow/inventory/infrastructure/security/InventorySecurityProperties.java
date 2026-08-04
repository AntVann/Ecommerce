package com.marketflow.inventory.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.inventory")
public record InventorySecurityProperties(
        String identityBaseUrl,
        String identityIssuer,
        String identityAudience,
        String sellerBaseUrl,
        String internalServiceKey) {}
