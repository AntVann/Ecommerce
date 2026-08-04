package com.marketflow.identity.application;

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
public class IdentityRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdentityRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<Account> findAccountByEmail(String normalizedEmail, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return jdbc
                .query(
                        """
                        SELECT u.id, u.email, u.normalized_email, u.status, u.failed_login_count,
                               u.locked_until, u.token_invalid_before, u.version, c.password_hash
                        FROM user_account u JOIN credential c ON c.user_id = u.id
                        WHERE u.normalized_email = ?
                        """
                                + lock,
                        IdentityRepository::mapAccount,
                        normalizedEmail)
                .stream()
                .findFirst();
    }

    public Optional<Account> findAccountById(UUID userId, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return jdbc
                .query(
                        """
                        SELECT u.id, u.email, u.normalized_email, u.status, u.failed_login_count,
                               u.locked_until, u.token_invalid_before, u.version, c.password_hash
                        FROM user_account u JOIN credential c ON c.user_id = u.id
                        WHERE u.id = ?
                        """
                                + lock,
                        IdentityRepository::mapAccount,
                        userId)
                .stream()
                .findFirst();
    }

    public void insertAccount(
            UUID userId,
            String email,
            String normalizedEmail,
            String passwordHash,
            UUID verificationId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO user_account(
                    id, email, normalized_email, status, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING_VERIFICATION', ?, ?)
                """,
                userId,
                email,
                normalizedEmail,
                db(now),
                db(now));
        jdbc.update(
                "INSERT INTO credential(user_id, password_hash, changed_at) VALUES (?, ?, ?)",
                userId,
                passwordHash,
                db(now));
        jdbc.update(
                """
                INSERT INTO role_assignment(id, user_id, role_code, granted_at)
                VALUES (?, ?, 'CUSTOMER', ?)
                """,
                UUID.randomUUID(),
                userId,
                db(now));
        insertVerification(verificationId, userId, now);
    }

    public void insertVerification(UUID verificationId, UUID userId, Instant now) {
        jdbc.update(
                """
                INSERT INTO email_verification(id, user_id, status, created_at)
                VALUES (?, ?, 'QUEUED', ?)
                """,
                verificationId,
                userId,
                db(now));
    }

    public long incrementAccountVersion(UUID userId, Instant now) {
        Long version =
                jdbc.queryForObject(
                        """
                        UPDATE user_account
                        SET version = version + 1, updated_at = ?
                        WHERE id = ?
                        RETURNING version
                        """,
                        Long.class,
                        db(now),
                        userId);
        if (version == null) {
            throw new IllegalStateException("Account version was not returned");
        }
        return version;
    }

    public void cancelVerifications(UUID userId) {
        jdbc.update(
                """
                UPDATE email_verification SET status = 'CANCELLED'
                WHERE user_id = ? AND status IN ('QUEUED', 'ISSUED')
                """,
                userId);
    }

    public Optional<Verification> lockVerification(UUID verificationId) {
        return jdbc
                .query(
                        """
                        SELECT id, user_id, status, token_digest, expires_at
                        FROM email_verification WHERE id = ? FOR UPDATE
                        """,
                        (rs, row) ->
                                new Verification(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getString("token_digest"),
                                        rs.getObject("expires_at", java.time.OffsetDateTime.class)
                                                        == null
                                                ? null
                                                : rs.getObject(
                                                                "expires_at",
                                                                java.time.OffsetDateTime.class)
                                                        .toInstant()),
                        verificationId)
                .stream()
                .findFirst();
    }

    public void issueVerification(
            UUID verificationId, String tokenDigest, Instant now, Instant expiresAt) {
        jdbc.update(
                """
                UPDATE email_verification
                SET status = 'ISSUED', token_digest = ?, issued_at = ?, expires_at = ?
                WHERE id = ? AND status = 'QUEUED'
                """,
                tokenDigest,
                db(now),
                db(expiresAt),
                verificationId);
    }

    public void consumeVerification(UUID verificationId, UUID userId, Instant now) {
        jdbc.update(
                """
                UPDATE email_verification SET status = 'CONSUMED', consumed_at = ? WHERE id = ?
                """,
                db(now),
                verificationId);
        jdbc.update(
                """
                UPDATE user_account
                SET status = 'ACTIVE', email_verified_at = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND status = 'PENDING_VERIFICATION'
                """,
                db(now),
                db(now),
                userId);
    }

    public List<String> roles(UUID userId) {
        return jdbc.queryForList(
                "SELECT role_code FROM role_assignment WHERE user_id = ? ORDER BY role_code",
                String.class,
                userId);
    }

    public void recordLoginFailure(UUID userId, Instant now) {
        jdbc.update(
                """
                UPDATE user_account
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN ? ELSE locked_until END,
                    updated_at = ?
                WHERE id = ?
                """,
                db(now.plusSeconds(900)),
                db(now),
                userId);
    }

    public void resetLoginFailures(UUID userId, Instant now) {
        jdbc.update(
                """
                UPDATE user_account
                SET failed_login_count = 0, locked_until = NULL, updated_at = ? WHERE id = ?
                """,
                db(now),
                userId);
    }

    public void updatePasswordHash(UUID userId, String passwordHash, Instant now) {
        jdbc.update(
                "UPDATE credential SET password_hash = ?, changed_at = ? WHERE user_id = ?",
                passwordHash,
                db(now),
                userId);
    }

    public void createSession(
            UUID familyId,
            UUID tokenId,
            UUID userId,
            String digest,
            Instant now,
            Instant idleExpiry,
            Instant absoluteExpiry) {
        jdbc.update(
                """
                INSERT INTO refresh_token_family(
                    id, user_id, status, created_at, last_used_at, absolute_expires_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?)
                """,
                familyId,
                userId,
                db(now),
                db(now),
                db(absoluteExpiry));
        jdbc.update(
                """
                INSERT INTO refresh_token(
                    id, family_id, token_digest, status, issued_at, expires_at)
                VALUES (?, ?, ?, 'CURRENT', ?, ?)
                """,
                tokenId,
                familyId,
                digest,
                db(now),
                db(idleExpiry));
    }

    public Optional<RefreshSession> lockRefreshToken(String digest) {
        return jdbc
                .query(
                        """
                        SELECT t.id token_id, t.family_id, t.status token_status, t.expires_at,
                               f.user_id, f.status family_status, f.absolute_expires_at,
                               u.status account_status
                        FROM refresh_token t
                        JOIN refresh_token_family f ON f.id = t.family_id
                        JOIN user_account u ON u.id = f.user_id
                        WHERE t.token_digest = ?
                        FOR UPDATE OF t, f, u
                        """,
                        (rs, row) ->
                                new RefreshSession(
                                        rs.getObject("token_id", UUID.class),
                                        rs.getObject("family_id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getString("token_status"),
                                        rs.getString("family_status"),
                                        rs.getString("account_status"),
                                        rs.getObject("expires_at", java.time.OffsetDateTime.class)
                                                .toInstant(),
                                        rs.getObject(
                                                        "absolute_expires_at",
                                                        java.time.OffsetDateTime.class)
                                                .toInstant()),
                        digest)
                .stream()
                .findFirst();
    }

    public void rotateRefreshToken(
            RefreshSession current,
            UUID replacementId,
            String replacementDigest,
            Instant now,
            Instant expiresAt) {
        jdbc.update(
                """
                INSERT INTO refresh_token(
                    id, family_id, token_digest, status, issued_at, expires_at)
                VALUES (?, ?, ?, 'CURRENT', ?, ?)
                """,
                replacementId,
                current.familyId(),
                replacementDigest,
                db(now),
                db(expiresAt));
        jdbc.update(
                """
                UPDATE refresh_token
                SET status = 'ROTATED', consumed_at = ?, replaced_by = ? WHERE id = ?
                """,
                db(now),
                replacementId,
                current.tokenId());
        jdbc.update(
                "UPDATE refresh_token_family SET last_used_at = ? WHERE id = ?",
                db(now),
                current.familyId());
    }

    public void revokeFamily(UUID familyId, String status, String reason, Instant now) {
        jdbc.update(
                """
                UPDATE refresh_token_family SET status = ?, revoked_at = ?, revoke_reason = ?
                WHERE id = ? AND status = 'ACTIVE'
                """,
                status,
                db(now),
                reason,
                familyId);
        jdbc.update(
                "UPDATE refresh_token SET status = 'REVOKED' WHERE family_id = ? AND status = 'CURRENT'",
                familyId);
    }

    public void revokeAllSessions(UUID userId, String reason, Instant now) {
        List<UUID> familyIds =
                jdbc.queryForList(
                        "SELECT id FROM refresh_token_family WHERE user_id = ? AND status = 'ACTIVE'",
                        UUID.class,
                        userId);
        familyIds.forEach(id -> revokeFamily(id, "REVOKED", reason, now));
    }

    public void revokeAccessToken(
            UUID tokenId, UUID userId, Instant expiresAt, String reason, Instant now) {
        jdbc.update(
                """
                INSERT INTO access_token_revocation(token_id, user_id, expires_at, revoked_at, reason)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (token_id) DO NOTHING
                """,
                tokenId,
                userId,
                db(expiresAt),
                db(now),
                reason);
    }

    public boolean isAccessTokenRevoked(UUID tokenId, Instant now) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM access_token_revocation WHERE token_id = ? AND expires_at > ?",
                        Integer.class,
                        tokenId,
                        db(now));
        return count != null && count > 0;
    }

    public boolean disableAccount(UUID userId, Instant now) {
        return jdbc.update(
                        """
                        UPDATE user_account SET status = 'DISABLED', disabled_at = ?,
                            token_invalid_before = ?, updated_at = ?, version = version + 1
                        WHERE id = ? AND status <> 'DISABLED'
                        """,
                        db(now),
                        db(now),
                        db(now),
                        userId)
                > 0;
    }

    public void audit(
            String eventType,
            UUID actorId,
            UUID subjectId,
            String outcome,
            String reason,
            String correlationId,
            String sourceHash,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO security_event(
                    id, event_type, actor_user_id, subject_user_id, outcome, reason_code,
                    correlation_id, source_hash, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventType,
                actorId,
                subjectId,
                outcome,
                reason,
                correlationId,
                sourceHash,
                db(now));
    }

    public void outbox(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            String correlationId,
            Map<String, Object> data,
            Instant now) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", eventId),
                        Map.entry("eventType", eventType),
                        Map.entry("aggregateType", aggregateType),
                        Map.entry("aggregateId", aggregateId),
                        Map.entry("aggregateVersion", aggregateVersion),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "identity-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                """
                INSERT INTO outbox_event(
                    event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
                    correlation_id, payload, occurred_at, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                aggregateVersion,
                correlationId,
                json(envelope),
                db(now),
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

    private static Account mapAccount(ResultSet rs, int row) throws SQLException {
        var locked = rs.getObject("locked_until", java.time.OffsetDateTime.class);
        var invalid = rs.getObject("token_invalid_before", java.time.OffsetDateTime.class);
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("normalized_email"),
                rs.getString("status"),
                rs.getInt("failed_login_count"),
                locked == null ? null : locked.toInstant(),
                invalid == null ? null : invalid.toInstant(),
                rs.getLong("version"),
                rs.getString("password_hash"));
    }

    public record Account(
            UUID id,
            String email,
            String normalizedEmail,
            String status,
            int failedLoginCount,
            Instant lockedUntil,
            Instant tokenInvalidBefore,
            long version,
            String passwordHash) {}

    public record Verification(
            UUID id, UUID userId, String status, String tokenDigest, Instant expiresAt) {}

    public record RefreshSession(
            UUID tokenId,
            UUID familyId,
            UUID userId,
            String tokenStatus,
            String familyStatus,
            String accountStatus,
            Instant expiresAt,
            Instant absoluteExpiresAt) {}
}
