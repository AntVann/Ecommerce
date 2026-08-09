package com.marketflow.payment.infrastructure.provider;

import com.marketflow.payment.application.PaymentProvider;
import com.marketflow.payment.domain.PaymentStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentProvider implements PaymentProvider {
    private final FakeProviderProperties properties;
    private final Map<String, ProviderResult> results = new ConcurrentHashMap<>();

    public FakePaymentProvider(FakeProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderResult authorize(ProviderCommand command) {
        ProviderResult previous = results.get(command.idempotencyKey());
        if (previous != null) {
            return new ProviderResult(
                    previous.outcome(),
                    previous.providerReference(),
                    previous.reasonCode(),
                    List.of());
        }
        if (!properties.available()) {
            ProviderResult unavailable =
                    result(PaymentStatus.UNKNOWN, command.paymentId(), "PROVIDER_UNAVAILABLE");
            results.put(command.idempotencyKey(), unavailable);
            return unavailable;
        }
        String reference = command.paymentId().toString();
        ProviderResult result =
                switch (command.opaqueToken()) {
                    case "mf_fake_approve" ->
                            result(PaymentStatus.AUTHORIZED, command.paymentId(), null);
                    case "mf_fake_decline" ->
                            result(PaymentStatus.DECLINED, command.paymentId(), "FAKE_DECLINED");
                    case "mf_fake_timeout" ->
                            result(PaymentStatus.UNKNOWN, command.paymentId(), "PROVIDER_TIMEOUT");
                    case "mf_fake_delayed_approve" ->
                            delayed(reference, PaymentStatus.AUTHORIZED, null, 1);
                    case "mf_fake_delayed_decline" ->
                            delayed(reference, PaymentStatus.DECLINED, "FAKE_DECLINED", 1);
                    case "mf_fake_duplicate" ->
                            delayed(
                                    reference,
                                    PaymentStatus.AUTHORIZED,
                                    null,
                                    properties.duplicateCallbacks());
                    default -> throw new IllegalArgumentException("PAYMENT_TOKEN_UNSUPPORTED");
                };
        results.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    public ProviderResult reconcile(String idempotencyKey, String providerReference) {
        ProviderResult result = results.get(idempotencyKey);
        if (result == null || result.outcome() == PaymentStatus.PROCESSING) {
            return new ProviderResult(
                    PaymentStatus.UNKNOWN,
                    providerReference,
                    "RECONCILIATION_UNRESOLVED",
                    List.of());
        }
        return new ProviderResult(
                result.outcome(), providerReference, result.reasonCode(), List.of());
    }

    @Override
    public boolean available() {
        return properties.available();
    }

    private ProviderResult delayed(
            String reference, PaymentStatus outcome, String reason, int count) {
        UUID eventId = UUID.randomUUID();
        List<CallbackPlan> callbacks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            callbacks.add(
                    new CallbackPlan(
                            eventId,
                            reference,
                            outcome,
                            reason,
                            properties.callbackDelay().plusMillis(index * 25L)));
        }
        return new ProviderResult(PaymentStatus.PROCESSING, reference, null, callbacks);
    }

    private static ProviderResult result(PaymentStatus status, UUID id, String reason) {
        return new ProviderResult(status, id.toString(), reason, List.of());
    }
}
