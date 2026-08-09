package com.marketflow.notification.infrastructure.persistence;

import com.marketflow.notification.application.NotificationModels.CreateCommand;
import com.marketflow.notification.application.NotificationModels.NotificationView;
import com.marketflow.notification.application.NotificationStore;
import com.marketflow.notification.domain.NotificationKind;
import com.marketflow.notification.domain.NotificationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationStore implements NotificationStore {
    private final JdbcTemplate jdbc;

    public JdbcNotificationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claimEvent(String consumer, UUID eventId, Instant now) {
        return jdbc.update(
                        "INSERT INTO notification_inbox(consumer,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                        consumer,
                        eventId,
                        Timestamp.from(now))
                == 1;
    }

    @Override
    public UUID createOrGet(CreateCommand c, Instant now) {
        UUID id = UUID.randomUUID();
        int changed =
                jdbc.update(
                        "INSERT INTO notification_job(id,source_event_id,customer_id,order_id,kind,recipient,template_key,template_version,variables,status,attempt_count,next_attempt_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?::jsonb,'QUEUED',0,?,?,?) ON CONFLICT(source_event_id,kind) DO NOTHING",
                        id,
                        c.sourceEventId(),
                        c.customerId(),
                        c.orderId(),
                        c.kind().name(),
                        c.recipient(),
                        c.templateKey(),
                        c.templateVersion(),
                        c.variablesJson(),
                        Timestamp.from(now),
                        Timestamp.from(now),
                        Timestamp.from(now));
        if (changed == 1) {
            jdbc.update(
                    "INSERT INTO notification_outbox(id,job_id,routing_key,payload,created_at) VALUES (?,?,?,?::jsonb,?)",
                    UUID.randomUUID(),
                    id,
                    routing(c.kind()),
                    "{\"jobId\":\"" + id + "\"}",
                    Timestamp.from(now));
            return id;
        }
        return jdbc.queryForObject(
                "SELECT id FROM notification_job WHERE source_event_id=? AND kind=?",
                UUID.class,
                c.sourceEventId(),
                c.kind().name());
    }

    private String routing(NotificationKind kind) {
        return kind == NotificationKind.ORDER_CONFIRMATION
                ? "notification.email.order-confirmation.v1"
                : "notification.email.shipment.v1";
    }

    @Override
    public Optional<NotificationView> find(UUID id) {
        return jdbc
                .query(
                        "SELECT id,source_event_id,customer_id,order_id,kind,recipient,template_key,template_version,status,attempt_count,last_error,created_at,updated_at FROM notification_job WHERE id=?",
                        (r, n) -> map(r),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<NotificationView> claimDue(UUID id, Instant now) {
        int changed =
                jdbc.update(
                        "UPDATE notification_job SET status='PROCESSING',attempt_count=attempt_count+1,updated_at=? WHERE id=? AND status IN ('QUEUED','RETRY_SCHEDULED') AND next_attempt_at<=?",
                        Timestamp.from(now),
                        id,
                        Timestamp.from(now));
        if (changed == 0) {
            return find(id).filter(view -> view.status() == NotificationStatus.DELIVERED);
        }
        return find(id);
    }

    @Override
    public UUID recordAttempt(UUID jobId, int attempt, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO notification_attempt(id,job_id,attempt_number,status,created_at) VALUES (?,?,?,'PROCESSING',?)",
                id,
                jobId,
                attempt,
                Timestamp.from(now));
        return id;
    }

    @Override
    public void delivered(UUID jobId, UUID attemptId, String providerId, Instant now) {
        jdbc.update(
                "UPDATE notification_attempt SET status='DELIVERED',provider_message_id=? WHERE id=?",
                providerId,
                attemptId);
        jdbc.update(
                "UPDATE notification_job SET status='DELIVERED',updated_at=? WHERE id=?",
                Timestamp.from(now),
                jobId);
    }

    @Override
    public void retry(UUID jobId, UUID attemptId, String reason, Instant next, Instant now) {
        jdbc.update(
                "UPDATE notification_attempt SET status='RETRY_SCHEDULED',failure_code=? WHERE id=?",
                reason,
                attemptId);
        jdbc.update(
                "UPDATE notification_job SET status='RETRY_SCHEDULED',last_error=?,next_attempt_at=?,updated_at=? WHERE id=?",
                reason,
                Timestamp.from(next),
                Timestamp.from(now),
                jobId);
        jdbc.update(
                "INSERT INTO notification_outbox(id,job_id,routing_key,payload,created_at) SELECT ?,id,CASE WHEN attempt_count=1 THEN 'notification.email.retry.1.v1' WHEN attempt_count=2 THEN 'notification.email.retry.2.v1' ELSE 'notification.email.retry.3.v1' END,?::jsonb,? FROM notification_job WHERE id=?",
                UUID.randomUUID(),
                "{\"jobId\":\"" + jobId + "\"}",
                Timestamp.from(now),
                jobId);
    }

    @Override
    public void deadLetter(UUID jobId, UUID attemptId, String reason, Instant now) {
        jdbc.update(
                "UPDATE notification_attempt SET status='DEAD_LETTERED',failure_code=? WHERE id=?",
                reason,
                attemptId);
        jdbc.update(
                "UPDATE notification_job SET status='DEAD_LETTERED',last_error=?,updated_at=? WHERE id=?",
                reason,
                Timestamp.from(now),
                jobId);
    }

    private NotificationView map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new NotificationView(
                r.getObject(1, UUID.class),
                r.getObject(2, UUID.class),
                r.getObject(3, UUID.class),
                r.getObject(4, UUID.class),
                NotificationKind.valueOf(r.getString(5)),
                r.getString(6),
                r.getString(7),
                r.getInt(8),
                NotificationStatus.valueOf(r.getString(9)),
                r.getInt(10),
                r.getString(11),
                r.getTimestamp(12).toInstant(),
                r.getTimestamp(13).toInstant());
    }
}
