package com.marketflow.seller.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SellerRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SellerRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<SellerRecord> findSeller(UUID sellerId, boolean forUpdate) {
        return jdbc
                .query(
                        """
                        SELECT id, applicant_user_id, display_name, legal_name, country_code,
                               status, version, created_at, updated_at
                        FROM seller WHERE id = ?
                        """
                                + (forUpdate ? " FOR UPDATE" : ""),
                        SellerRepository::mapSeller,
                        sellerId)
                .stream()
                .findFirst();
    }

    public List<SellerRecord> list(String status, int limit) {
        if (status == null) {
            return jdbc.query(
                    """
                    SELECT id, applicant_user_id, display_name, legal_name, country_code,
                           status, version, created_at, updated_at
                    FROM seller ORDER BY created_at DESC LIMIT ?
                    """,
                    SellerRepository::mapSeller,
                    limit);
        }
        return jdbc.query(
                """
                SELECT id, applicant_user_id, display_name, legal_name, country_code,
                       status, version, created_at, updated_at
                FROM seller WHERE status = ? ORDER BY created_at DESC LIMIT ?
                """,
                SellerRepository::mapSeller,
                status,
                limit);
    }

    public UUID createApplication(
            UUID applicantId,
            String displayName,
            String legalName,
            String countryCode,
            String correlationId,
            Instant now) {
        UUID sellerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO seller(
                    id, applicant_user_id, display_name, legal_name, country_code,
                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, ?)
                """,
                sellerId,
                applicantId,
                displayName,
                legalName,
                countryCode,
                db(now),
                db(now));
        jdbc.update(
                """
                INSERT INTO seller_application(id, seller_id, submitted_by, submitted_at)
                VALUES (?, ?, ?, ?)
                """,
                applicationId,
                sellerId,
                applicantId,
                db(now));
        history(
                sellerId,
                null,
                "PENDING_REVIEW",
                applicantId,
                "APPLICATION_SUBMITTED",
                correlationId,
                now);
        return sellerId;
    }

    public boolean transition(
            UUID sellerId,
            long expectedVersion,
            String nextStatus,
            UUID actorId,
            String reason,
            String correlationId,
            Instant now) {
        SellerRecord before = findSeller(sellerId, true).orElse(null);
        if (before == null || before.version() != expectedVersion) {
            return false;
        }
        int updated =
                jdbc.update(
                        """
                UPDATE seller SET status = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                        nextStatus,
                        db(now),
                        sellerId,
                        expectedVersion);
        if (updated == 1) {
            jdbc.update(
                    """
                    UPDATE seller_application SET reviewed_by = ?, reviewed_at = ?, decision_reason = ?
                    WHERE seller_id = ?
                    """,
                    actorId,
                    db(now),
                    reason,
                    sellerId);
            history(sellerId, before.status(), nextStatus, actorId, reason, correlationId, now);
        }
        return updated == 1;
    }

    public void addOwner(UUID sellerId, UUID userId, Instant now) {
        jdbc.update(
                """
                INSERT INTO seller_membership(
                    id, seller_id, user_id, role_code, created_at, updated_at)
                VALUES (?, ?, ?, 'OWNER', ?, ?) ON CONFLICT (seller_id, user_id) DO NOTHING
                """,
                UUID.randomUUID(),
                sellerId,
                userId,
                db(now),
                db(now));
    }

    public Optional<Membership> membership(UUID sellerId, UUID userId) {
        return jdbc
                .query(
                        """
                        SELECT id, seller_id, user_id, role_code, version
                        FROM seller_membership WHERE seller_id = ? AND user_id = ?
                        """,
                        (rs, row) ->
                                new Membership(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("seller_id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getString("role_code"),
                                        rs.getLong("version")),
                        sellerId,
                        userId)
                .stream()
                .findFirst();
    }

    public boolean hasPermission(UUID sellerId, UUID userId, String permission) {
        Integer count =
                jdbc.queryForObject(
                        """
                SELECT count(*) FROM seller_membership m
                JOIN seller_role_permission p ON p.role_code = m.role_code
                WHERE m.seller_id = ? AND m.user_id = ? AND p.permission_code = ?
                """,
                        Integer.class,
                        sellerId,
                        userId,
                        permission);
        return count != null && count > 0;
    }

    public void addMember(UUID sellerId, UUID userId, String role, Instant now) {
        jdbc.update(
                """
                INSERT INTO seller_membership(
                    id, seller_id, user_id, role_code, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                sellerId,
                userId,
                role,
                db(now),
                db(now));
    }

    public boolean changeMemberRole(
            UUID sellerId, UUID userId, String role, long expectedVersion, Instant now) {
        return jdbc.update(
                        """
                        UPDATE seller_membership
                        SET role_code = ?, version = version + 1, updated_at = ?
                        WHERE seller_id = ? AND user_id = ? AND role_code <> 'OWNER' AND version = ?
                        """,
                        role,
                        db(now),
                        sellerId,
                        userId,
                        expectedVersion)
                == 1;
    }

    public boolean removeMember(UUID sellerId, UUID userId, long expectedVersion) {
        return jdbc.update(
                        """
                        DELETE FROM seller_membership
                        WHERE seller_id = ? AND user_id = ? AND role_code <> 'OWNER' AND version = ?
                        """,
                        sellerId,
                        userId,
                        expectedVersion)
                == 1;
    }

    public Optional<IdempotencyRecord> idempotency(String operation, String key) {
        return jdbc
                .query(
                        """
                        SELECT request_hash, resource_id, resource_version
                        FROM idempotency_record
                        WHERE operation = ? AND idempotency_key = ? AND expires_at > now()
                        """,
                        (rs, row) ->
                                new IdempotencyRecord(
                                        rs.getString("request_hash"),
                                        rs.getObject("resource_id", UUID.class),
                                        rs.getLong("resource_version")),
                        operation,
                        key)
                .stream()
                .findFirst();
    }

    public void saveIdempotency(
            String operation,
            String key,
            String requestHash,
            UUID resourceId,
            long resourceVersion,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO idempotency_record(
                    operation, idempotency_key, request_hash, response_status, resource_id,
                    resource_version, created_at, expires_at)
                VALUES (?, ?, ?, 200, ?, ?, ?, ?)
                """,
                operation,
                key,
                requestHash,
                resourceId,
                resourceVersion,
                db(now),
                db(now.plusSeconds(86400)));
    }

    public void audit(
            String eventType,
            UUID actorId,
            UUID sellerId,
            String outcome,
            String reason,
            String correlationId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO security_event(
                    id, event_type, actor_user_id, seller_id, outcome, reason_code,
                    correlation_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventType,
                actorId,
                sellerId,
                outcome,
                reason,
                correlationId,
                db(now));
    }

    public void outbox(
            String eventType,
            UUID sellerId,
            long version,
            String correlationId,
            Map<String, Object> data,
            Instant now) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", eventId),
                        Map.entry("eventType", eventType),
                        Map.entry("aggregateType", "Seller"),
                        Map.entry("aggregateId", sellerId),
                        Map.entry("aggregateVersion", version),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "seller-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                """
                INSERT INTO outbox_event(
                    event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                    correlation_id, payload, occurred_at, next_attempt_at)
                VALUES (?, ?, 'Seller', ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                eventId,
                eventType,
                sellerId,
                version,
                correlationId,
                json(envelope),
                db(now),
                db(now));
    }

    private void history(
            UUID sellerId,
            String previous,
            String next,
            UUID actor,
            String reason,
            String correlationId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO seller_status_history(
                    id, seller_id, previous_status, new_status, actor_user_id, reason,
                    correlation_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                sellerId,
                previous,
                next,
                actor,
                reason,
                correlationId,
                db(now));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event envelope", exception);
        }
    }

    private static java.sql.Timestamp db(Instant value) {
        return java.sql.Timestamp.from(value);
    }

    private static SellerRecord mapSeller(ResultSet rs, int row) throws SQLException {
        return new SellerRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("applicant_user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("legal_name"),
                rs.getString("country_code"),
                rs.getString("status"),
                rs.getLong("version"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }

    public record SellerRecord(
            UUID id,
            UUID applicantUserId,
            String displayName,
            String legalName,
            String countryCode,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Membership(UUID id, UUID sellerId, UUID userId, String role, long version) {}

    public record IdempotencyRecord(String requestHash, UUID resourceId, long resourceVersion) {}
}
