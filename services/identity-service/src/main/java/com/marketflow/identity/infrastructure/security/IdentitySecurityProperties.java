package com.marketflow.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.identity")
public record IdentitySecurityProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshIdleTtl,
        Duration refreshAbsoluteTtl,
        Duration verificationTtl,
        String internalServiceKey,
        String rateLimitKey,
        int loginLimit,
        Duration loginWindow,
        boolean secureCookies) {}
