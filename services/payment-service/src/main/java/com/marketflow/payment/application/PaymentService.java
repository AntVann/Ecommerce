package com.marketflow.payment.application;

import com.marketflow.payment.application.PaymentModels.AuthorizationCommand;
import com.marketflow.payment.application.PaymentModels.CallbackCommand;
import com.marketflow.payment.application.PaymentModels.PaymentView;
import com.marketflow.payment.application.PaymentProvider.CallbackPlan;
import com.marketflow.payment.application.PaymentProvider.ProviderCommand;
import com.marketflow.payment.application.PaymentProvider.ProviderResult;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);
    private static final String OPERATION = "AUTHORIZE_PAYMENT";
    private final PaymentStore store;
    private final PaymentProvider provider;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final MeterRegistry meters;
    private final TransactionTemplate transactions;

    public PaymentService(
            PaymentStore store,
            PaymentProvider provider,
            Clock clock,
            TaskScheduler scheduler,
            MeterRegistry meters,
            TransactionTemplate transactions) {
        this.store = store;
        this.provider = provider;
        this.clock = clock;
        this.scheduler = scheduler;
        this.meters = meters;
        this.transactions = transactions;
    }

    @Transactional
    public PaymentView authorize(
            AuthorizationCommand command, String idempotencyKey, String correlationId) {
        validateKey(idempotencyKey);
        String hash = requestHash(command);
        Instant now = clock.instant();
        if (!store.claimIdempotency(OPERATION, idempotencyKey, hash, now)) {
            PaymentStore.IdempotencyRecord previous =
                    store.idempotency(OPERATION, idempotencyKey)
                            .orElseThrow(
                                    () -> new PaymentException("PAYMENT_IDEMPOTENCY_CONFLICT"));
            if (!MessageDigest.isEqual(
                    previous.requestHash().getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8))) {
                throw new PaymentException("PAYMENT_IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            if (previous.paymentId() == null) {
                throw new PaymentException("PAYMENT_REQUEST_IN_PROGRESS");
            }
            return required(previous.paymentId());
        }

        UUID paymentId = UUID.randomUUID();
        Payment payment =
                Payment.create(
                                paymentId,
                                command.orderId(),
                                command.customerId(),
                                command.amount(),
                                command.currency(),
                                now)
                        .processing(now);
        store.create(payment);
        UUID attemptId = store.createAttempt(paymentId, idempotencyKey, now);
        Timer.Sample sample = Timer.start(meters);
        ProviderResult result =
                provider.authorize(
                        new ProviderCommand(
                                paymentId,
                                idempotencyKey,
                                command.amount(),
                                command.currency(),
                                command.fakePaymentToken()));
        sample.stop(
                Timer.builder("payment.provider.authorization")
                        .tag("outcome", result.outcome().name().toLowerCase())
                        .register(meters));
        applyProviderResult(paymentId, attemptId, result, correlationId, now);
        store.completeIdempotency(OPERATION, idempotencyKey, paymentId);
        result.callbacks().forEach(plan -> schedule(plan, correlationId));
        PaymentView view = required(paymentId);
        LOG.atInfo()
                .addKeyValue("operation", "payment_authorization")
                .addKeyValue("payment.id", paymentId)
                .addKeyValue("order.id", command.orderId())
                .addKeyValue("outcome", view.status())
                .addKeyValue("correlation.id", correlationId)
                .log("Payment authorization completed");
        return view;
    }

    @Transactional
    public PaymentView callback(CallbackCommand command, String correlationId) {
        if (command.status() != PaymentStatus.AUTHORIZED
                && command.status() != PaymentStatus.DECLINED
                && command.status() != PaymentStatus.FAILED) {
            throw new PaymentException("PAYMENT_CALLBACK_OUTCOME_INVALID");
        }
        Instant now = clock.instant();
        PaymentView payment =
                store.byProviderReference(command.providerReference())
                        .orElseThrow(
                                () -> new PaymentException("PAYMENT_PROVIDER_REFERENCE_UNKNOWN"));
        if (!store.recordCallback(
                command.providerEventId(),
                command.providerReference(),
                command.status(),
                command.reasonCode(),
                now)) {
            meters.counter("payment.provider.callback", "outcome", "duplicate").increment();
            return required(payment.paymentId());
        }
        store.completeAttemptByReference(
                command.providerReference(), command.status(), command.reasonCode(), now);
        boolean changed =
                store.transition(
                        payment.paymentId(),
                        payment.status(),
                        command.status(),
                        command.reasonCode(),
                        now);
        if (changed) {
            PaymentView updated = required(payment.paymentId());
            store.outbox(updated, eventType(updated.status()), command.reasonCode(), correlationId);
            meters.counter("payment.provider.callback", "outcome", "applied").increment();
            return updated;
        }
        meters.counter("payment.provider.callback", "outcome", "ignored_terminal").increment();
        return required(payment.paymentId());
    }

    @Transactional
    public PaymentView reconcile(UUID paymentId, String idempotencyKey, String correlationId) {
        PaymentView payment = required(paymentId);
        if (payment.status() != PaymentStatus.UNKNOWN) {
            return payment;
        }
        PaymentStore.ReconciliationContext context =
                store.reconciliationContext(paymentId)
                        .orElseThrow(
                                () ->
                                        new PaymentException(
                                                "PAYMENT_RECONCILIATION_CONTEXT_MISSING"));
        if (!MessageDigest.isEqual(
                context.idempotencyKey().getBytes(StandardCharsets.UTF_8),
                idempotencyKey.getBytes(StandardCharsets.UTF_8))) {
            throw new PaymentException("PAYMENT_IDEMPOTENCY_KEY_MISMATCH");
        }
        ProviderResult result =
                provider.reconcile(context.idempotencyKey(), context.providerReference());
        int attempts = store.incrementReconciliation(paymentId, clock.instant());
        if (result.outcome() == PaymentStatus.UNKNOWN) {
            if (attempts >= 3) {
                store.requireManualReview(paymentId, clock.instant());
                PaymentView review = required(paymentId);
                store.outbox(
                        review,
                        "payment.payment-unknown.v1",
                        "MANUAL_REVIEW_REQUIRED",
                        correlationId);
                return review;
            }
            return required(paymentId);
        }
        store.transition(
                paymentId,
                PaymentStatus.UNKNOWN,
                result.outcome(),
                result.reasonCode(),
                clock.instant());
        PaymentView updated = required(paymentId);
        store.outbox(updated, eventType(updated.status()), result.reasonCode(), correlationId);
        return updated;
    }

    private void applyProviderResult(
            UUID paymentId,
            UUID attemptId,
            ProviderResult result,
            String correlationId,
            Instant now) {
        store.providerResult(
                attemptId, result.providerReference(), result.outcome(), result.reasonCode(), now);
        if (result.outcome() == PaymentStatus.PROCESSING) {
            return;
        }
        store.transition(
                paymentId, PaymentStatus.PROCESSING, result.outcome(), result.reasonCode(), now);
        PaymentView view = required(paymentId);
        store.outbox(view, eventType(view.status()), result.reasonCode(), correlationId);
        meters.counter("payment.authorization", "outcome", view.status().name().toLowerCase())
                .increment();
    }

    private void schedule(CallbackPlan plan, String correlationId) {
        scheduler.schedule(
                () ->
                        transactions.executeWithoutResult(
                                transaction ->
                                        callback(
                                                new CallbackCommand(
                                                        plan.providerEventId(),
                                                        plan.providerReference(),
                                                        plan.outcome(),
                                                        plan.reasonCode()),
                                                correlationId)),
                clock.instant().plus(plan.delay()));
    }

    private PaymentView required(UUID paymentId) {
        return store.payment(paymentId)
                .orElseThrow(() -> new PaymentException("PAYMENT_NOT_FOUND"));
    }

    private static String eventType(PaymentStatus status) {
        return switch (status) {
            case AUTHORIZED -> "payment.payment-authorized.v1";
            case DECLINED -> "payment.payment-declined.v1";
            case UNKNOWN -> "payment.payment-unknown.v1";
            case FAILED -> "payment.payment-failed.v1";
            default -> throw new PaymentException("PAYMENT_EVENT_STATUS_INVALID");
        };
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new PaymentException("PAYMENT_IDEMPOTENCY_KEY_INVALID");
        }
    }

    private static String requestHash(AuthorizationCommand command) {
        String value =
                command.orderId()
                        + "|"
                        + command.customerId()
                        + "|"
                        + command.amount().toPlainString()
                        + "|"
                        + command.currency()
                        + "|"
                        + command.fakePaymentToken();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static final class PaymentException extends RuntimeException {
        public PaymentException(String code) {
            super(code);
        }
    }
}
