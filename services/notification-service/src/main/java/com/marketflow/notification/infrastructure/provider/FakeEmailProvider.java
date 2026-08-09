package com.marketflow.notification.infrastructure.provider;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class FakeEmailProvider {
    private final FakeEmailProperties properties;
    private final AtomicInteger calls = new AtomicInteger();

    public FakeEmailProvider(FakeEmailProperties properties) {
        this.properties = properties;
    }

    public EmailResult send(String recipient, String template, int version, String orderId) {
        int call = calls.incrementAndGet();
        String scenario =
                properties.scenario() == null ? "success" : properties.scenario().toLowerCase();
        if ("permanent-failure".equals(scenario) || "failure".equals(scenario))
            return new EmailResult(false, false, "PROVIDER_REJECTED", null);
        if ("timeout".equals(scenario))
            return new EmailResult(false, true, "PROVIDER_TIMEOUT", null);
        if ("transient-failure".equals(scenario) && call <= properties.transientFailures())
            return new EmailResult(false, true, "PROVIDER_UNAVAILABLE", null);
        return new EmailResult(true, false, null, "fake-email-" + call);
    }

    public record EmailResult(
            boolean success, boolean retryable, String reason, String providerMessageId) {}
}
