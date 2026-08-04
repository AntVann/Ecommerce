package com.marketflow.identity.application;

public interface LoginRateLimiter {

    void check(String normalizedEmail, String sourceAddress);
}
