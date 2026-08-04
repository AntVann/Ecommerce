package com.marketflow.identity.infrastructure.security;

import com.marketflow.identity.api.ApiException;
import com.marketflow.identity.application.LoginRateLimiter;
import com.marketflow.identity.application.SecretTokens;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class RedisLoginRateLimiter implements LoginRateLimiter {

    private static final DefaultRedisScript<Long> ATTEMPT =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
                    Long.class);

    private final StringRedisTemplate redis;
    private final IdentitySecurityProperties properties;
    private final Counter rateLimited;

    public RedisLoginRateLimiter(
            StringRedisTemplate redis,
            IdentitySecurityProperties properties,
            MeterRegistry registry) {
        this.redis = redis;
        this.properties = properties;
        this.rateLimited = registry.counter("login.rate.limited.total");
    }

    @Override
    public void check(String normalizedEmail, String sourceAddress) {
        String accountKey = privacyKey("account:" + normalizedEmail);
        String sourceKey = privacyKey("source:" + sourceAddress);
        try {
            long accountAttempts = increment("marketflow:login:" + accountKey);
            long sourceAttempts = increment("marketflow:login:" + sourceKey);
            if (accountAttempts > properties.loginLimit()
                    || sourceAttempts > properties.loginLimit() * 5L) {
                rateLimited.increment();
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AUTH_RATE_LIMITED_429",
                        "Too many authentication attempts. Try again later.");
            }
        } catch (DataAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_RATE_LIMIT_UNAVAILABLE_503",
                    "Authentication is temporarily unavailable.");
        }
    }

    private long increment(String key) {
        Long value =
                redis.execute(
                        ATTEMPT, List.of(key), Long.toString(properties.loginWindow().toSeconds()));
        if (value == null) {
            throw new IllegalStateException("Redis rate-limit script returned no result");
        }
        return value;
    }

    private String privacyKey(String value) {
        return SecretTokens.digest(properties.rateLimitKey() + ':' + value);
    }
}
