package com.marketflow.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.search")
public record SearchProperties(
        String openSearchUrl, String catalogBaseUrl, String internalServiceKey, String alias) {}
