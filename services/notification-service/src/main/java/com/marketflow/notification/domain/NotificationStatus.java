package com.marketflow.notification.domain;

public enum NotificationStatus {
    QUEUED,
    PROCESSING,
    DELIVERED,
    RETRY_SCHEDULED,
    DEAD_LETTERED
}
