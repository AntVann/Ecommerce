package com.marketflow.payment.infrastructure.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("marketflow.payment.fake-provider")
public record FakeProviderProperties(
        Duration callbackDelay,
        int duplicateCallbacks,
        String callbackSignature,
        boolean available) {
    public FakeProviderProperties {
        callbackDelay = callbackDelay == null ? Duration.ofSeconds(1) : callbackDelay;
        duplicateCallbacks = duplicateCallbacks < 2 ? 2 : duplicateCallbacks;
        callbackSignature =
                callbackSignature == null ? "local-fake-provider-signature" : callbackSignature;
    }
}
