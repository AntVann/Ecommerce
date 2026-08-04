package com.marketflow.catalog.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.catalog")
public record CatalogSecurityProperties(
        String identityBaseUrl,
        String identityIssuer,
        String identityAudience,
        String sellerBaseUrl,
        String internalServiceKey) {}
