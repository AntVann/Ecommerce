package com.marketflow.notification.application;

import com.marketflow.notification.application.NotificationModels.CreateCommand;
import com.marketflow.notification.application.NotificationModels.NotificationView;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStore {
    boolean claimEvent(String consumer, UUID eventId, Instant now);

    UUID createOrGet(CreateCommand command, Instant now);

    Optional<NotificationView> find(UUID id);

    Optional<NotificationView> claimDue(UUID id, Instant now);

    UUID recordAttempt(UUID jobId, int attempt, Instant now);

    void delivered(UUID jobId, UUID attemptId, String providerId, Instant now);

    void retry(UUID jobId, UUID attemptId, String reason, Instant next, Instant now);

    void deadLetter(UUID jobId, UUID attemptId, String reason, Instant now);
}
