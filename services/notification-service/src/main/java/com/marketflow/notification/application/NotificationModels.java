package com.marketflow.notification.application;

import com.marketflow.notification.domain.NotificationKind;
import com.marketflow.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.UUID;

public final class NotificationModels {
    private NotificationModels() {}

    public record NotificationView(
            UUID id,
            UUID sourceEventId,
            UUID customerId,
            UUID orderId,
            NotificationKind kind,
            String recipient,
            String templateKey,
            int templateVersion,
            NotificationStatus status,
            int attemptCount,
            String lastError,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateCommand(
            UUID sourceEventId,
            UUID customerId,
            UUID orderId,
            NotificationKind kind,
            String recipient,
            String templateKey,
            int templateVersion,
            String variablesJson) {}
}
