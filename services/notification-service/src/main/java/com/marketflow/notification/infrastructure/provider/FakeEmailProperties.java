package com.marketflow.notification.infrastructure.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketflow.notification.fake-email")
public record FakeEmailProperties(String scenario, int transientFailures) {}
