package com.marketflow.payment.infrastructure.persistence;

import com.marketflow.payment.application.PaymentModels.PaymentView;
import com.marketflow.payment.application.PaymentStore;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcPaymentStore implements PaymentStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcPaymentStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public boolean claimIdempotency(String operation, String key, String hash, Instant now) {
        return jdbc.update(
                        "INSERT INTO idempotency_record(operation,idempotency_key,request_hash,created_at,expires_at) VALUES (?,?,?,?,CAST(? AS TIMESTAMPTZ) + interval '24 hours') ON CONFLICT DO NOTHING",
                        operation,
                        key,
                        hash,
                        db(now),
                        db(now))
                == 1;
    }

    @Override
    public Optional<IdempotencyRecord> idempotency(String operation, String key) {
        return jdbc
                .query(
                        "SELECT request_hash,payment_id FROM idempotency_record WHERE operation=? AND idempotency_key=?",
                        (r, n) -> new IdempotencyRecord(r.getString(1), r.getObject(2, UUID.class)),
                        operation,
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public void completeIdempotency(String operation, String key, UUID paymentId) {
        jdbc.update(
                "UPDATE idempotency_record SET payment_id=? WHERE operation=? AND idempotency_key=?",
                paymentId,
                operation,
                key);
    }

    @Override
    public void create(Payment payment) {
        jdbc.update(
                "INSERT INTO payment(id,order_id,customer_id,amount,currency,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                payment.id(),
                payment.orderId(),
                payment.customerId(),
                payment.amount(),
                payment.currency(),
                payment.status().name(),
                payment.version(),
                db(payment.createdAt()),
                db(payment.updatedAt()));
    }

    @Override
    public UUID createAttempt(UUID paymentId, String key, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO payment_attempt(id,payment_id,attempt_number,idempotency_key,status,created_at) VALUES (?,?,1,?,'PROCESSING',?)",
                id,
                paymentId,
                key,
                db(now));
        return id;
    }

    @Override
    public void providerResult(
            UUID attemptId, String reference, PaymentStatus status, String reason, Instant now) {
        jdbc.update(
                "UPDATE payment_attempt SET provider_reference=?,status=?,reason_code=?,completed_at=CASE WHEN ?='PROCESSING' THEN NULL ELSE CAST(? AS TIMESTAMPTZ) END WHERE id=?",
                reference,
                status.name(),
                reason,
                status.name(),
                db(now),
                attemptId);
    }

    @Override
    public Optional<PaymentView> payment(UUID paymentId) {
        return jdbc.query("SELECT * FROM payment WHERE id=?", this::map, paymentId).stream()
                .findFirst();
    }

    @Override
    public Optional<PaymentView> byProviderReference(String reference) {
        return jdbc
                .query(
                        "SELECT p.* FROM payment p JOIN payment_attempt a ON a.payment_id=p.id WHERE a.provider_reference=?",
                        this::map,
                        reference)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ReconciliationContext> reconciliationContext(UUID paymentId) {
        return jdbc
                .query(
                        "SELECT idempotency_key,provider_reference FROM payment_attempt WHERE payment_id=? ORDER BY attempt_number DESC LIMIT 1",
                        (r, n) -> new ReconciliationContext(r.getString(1), r.getString(2)),
                        paymentId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean transition(
            UUID paymentId,
            PaymentStatus expected,
            PaymentStatus next,
            String reason,
            Instant now) {
        return jdbc.update(
                        "UPDATE payment SET status=?,reason_code=?,version=version+1,updated_at=? WHERE id=? AND status=?",
                        next.name(),
                        reason,
                        db(now),
                        paymentId,
                        expected.name())
                == 1;
    }

    @Override
    public boolean recordCallback(
            UUID eventId, String reference, PaymentStatus outcome, String reason, Instant now) {
        return jdbc.update(
                        "INSERT INTO provider_callback(provider_event_id,provider_reference,outcome,reason_code,received_at) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                        eventId,
                        reference,
                        outcome.name(),
                        reason,
                        db(now))
                == 1;
    }

    @Override
    public void completeAttemptByReference(
            String reference, PaymentStatus outcome, String reason, Instant now) {
        jdbc.update(
                "UPDATE payment_attempt SET status=?,reason_code=?,completed_at=? WHERE provider_reference=? AND status='PROCESSING'",
                outcome.name(),
                reason,
                db(now),
                reference);
    }

    @Override
    public void outbox(
            PaymentView payment, String eventType, String reasonCode, String correlationId) {
        UUID eventId = UUID.randomUUID();
        Instant now = payment.updatedAt();
        Map<String, Object> data =
                Map.ofEntries(
                        Map.entry("paymentId", payment.paymentId()),
                        Map.entry("orderId", payment.orderId()),
                        Map.entry("attemptId", payment.attemptId()),
                        Map.entry("amount", payment.amount()),
                        Map.entry("currency", payment.currency()),
                        Map.entry("reasonCode", reasonCode == null ? "NONE" : reasonCode),
                        Map.entry("manualReview", payment.manualReview()));
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", eventId),
                        Map.entry("eventType", eventType),
                        Map.entry("aggregateType", "Payment"),
                        Map.entry("aggregateId", payment.paymentId()),
                        Map.entry("aggregateVersion", payment.version()),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "payment-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                "INSERT INTO outbox_event(event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,payload,occurred_at,next_attempt_at) VALUES (?,?,'Payment',?,?,?,CAST(? AS jsonb),?,?)",
                eventId,
                eventType,
                payment.paymentId(),
                payment.version(),
                correlationId,
                json(envelope),
                db(now),
                db(now));
    }

    @Override
    public int incrementReconciliation(UUID paymentId, Instant now) {
        jdbc.update(
                "UPDATE payment SET reconciliation_attempts=reconciliation_attempts+1,updated_at=? WHERE id=? AND status='UNKNOWN'",
                db(now),
                paymentId);
        return jdbc.queryForObject(
                "SELECT reconciliation_attempts FROM payment WHERE id=?", Integer.class, paymentId);
    }

    @Override
    public void requireManualReview(UUID paymentId, Instant now) {
        jdbc.update(
                "UPDATE payment SET manual_review=TRUE,reason_code='MANUAL_REVIEW_REQUIRED',version=version+1,updated_at=? WHERE id=? AND status='UNKNOWN'",
                db(now),
                paymentId);
    }

    @Override
    public boolean claimMessage(String consumer, UUID eventId, Instant now) {
        return jdbc.update(
                        "INSERT INTO processed_message(consumer_name,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                        consumer,
                        eventId,
                        db(now))
                == 1;
    }

    private PaymentView map(ResultSet r, int row) throws SQLException {
        UUID paymentId = r.getObject("id", UUID.class);
        UUID attempt =
                jdbc
                        .query(
                                "SELECT id FROM payment_attempt WHERE payment_id=? ORDER BY attempt_number DESC LIMIT 1",
                                (a, n) -> a.getObject(1, UUID.class),
                                paymentId)
                        .stream()
                        .findFirst()
                        .orElse(null);
        return new PaymentView(
                paymentId,
                r.getObject("order_id", UUID.class),
                r.getObject("customer_id", UUID.class),
                r.getBigDecimal("amount"),
                r.getString("currency").trim(),
                PaymentStatus.valueOf(r.getString("status")),
                attempt,
                r.getString("reason_code"),
                r.getBoolean("manual_review"),
                r.getLong("version"),
                r.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize payment event", e);
        }
    }

    private static Timestamp db(Instant instant) {
        return Timestamp.from(instant);
    }
}
