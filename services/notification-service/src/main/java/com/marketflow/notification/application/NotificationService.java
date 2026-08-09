package com.marketflow.notification.application;

import com.marketflow.notification.application.NotificationModels.CreateCommand;
import com.marketflow.notification.application.NotificationModels.NotificationView;
import com.marketflow.notification.domain.NotificationStatus;
import com.marketflow.notification.infrastructure.provider.FakeEmailProvider;
import com.marketflow.notification.infrastructure.provider.FakeEmailProvider.EmailResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationStore store;
    private final FakeEmailProvider provider;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int maxAttempts;
    private final Duration baseDelay;

    public NotificationService(
            NotificationStore store,
            FakeEmailProvider provider,
            Clock clock,
            MeterRegistry meters,
            org.springframework.core.env.Environment environment) {
        this.store = store;
        this.provider = provider;
        this.clock = clock;
        this.meters = meters;
        this.maxAttempts =
                Integer.parseInt(
                        environment.getProperty("marketflow.notification.retry.max-attempts", "5"));
        this.baseDelay =
                Duration.parse(
                        environment.getProperty(
                                "marketflow.notification.retry.base-delay", "PT5S"));
    }

    @Transactional
    public UUID enqueue(CreateCommand command) {
        return store.createOrGet(command, clock.instant());
    }

    public Optional<NotificationView> find(UUID id) {
        return store.find(id);
    }

    @Transactional
    public void deliver(UUID id) {
        Instant now = clock.instant();
        Optional<NotificationView> claimed = store.claimDue(id, now);
        if (claimed.isEmpty() || claimed.get().status() == NotificationStatus.DELIVERED) return;
        NotificationView job = claimed.get();
        int attemptNumber = Math.max(1, job.attemptCount());
        UUID attemptId = store.recordAttempt(id, attemptNumber, now);
        EmailResult result =
                provider.send(
                        job.recipient(),
                        job.templateKey(),
                        job.templateVersion(),
                        job.orderId().toString());
        meters.counter("notification.delivery.attempts", "kind", job.kind().name()).increment();
        if (result.success()) {
            store.delivered(id, attemptId, result.providerMessageId(), clock.instant());
            meters.counter("notification.delivery.delivered").increment();
            LoggerFactory.getLogger(getClass())
                    .atInfo()
                    .addKeyValue("notification.id", id)
                    .log("Notification delivered");
        } else if (attemptNumber >= maxAttempts || !result.retryable()) {
            store.deadLetter(id, attemptId, result.reason(), clock.instant());
            meters.counter("notification.delivery.dead_letter").increment();
            LoggerFactory.getLogger(getClass())
                    .atWarn()
                    .addKeyValue("notification.id", id)
                    .addKeyValue("reason", result.reason())
                    .log("Notification dead-lettered");
        } else {
            long seconds =
                    baseDelay.multipliedBy(1L << Math.min(attemptNumber - 1, 10)).toSeconds();
            store.retry(
                    id,
                    attemptId,
                    result.reason(),
                    clock.instant().plusSeconds(seconds),
                    clock.instant());
            meters.counter("notification.delivery.retried").increment();
        }
    }
}
