package com.marketflow.inventory.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InventoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void ensureItem(UUID variantId, UUID sellerId, Instant now) {
        jdbc.update(
                "INSERT INTO inventory_item(variant_id,seller_id,created_at,updated_at) VALUES (?,?,?,?) ON CONFLICT (variant_id) DO NOTHING",
                variantId,
                sellerId,
                db(now),
                db(now));
    }

    public Optional<Item> item(UUID variantId) {
        return jdbc
                .query(
                        "SELECT variant_id,seller_id,on_hand,reserved,version,updated_at FROM inventory_item WHERE variant_id=?",
                        InventoryRepository::mapItem,
                        variantId)
                .stream()
                .findFirst();
    }

    public List<Item> sellerItems(UUID sellerId) {
        return jdbc.query(
                "SELECT variant_id,seller_id,on_hand,reserved,version,updated_at FROM inventory_item WHERE seller_id=? ORDER BY variant_id",
                InventoryRepository::mapItem,
                sellerId);
    }

    public List<Item> items(List<UUID> variantIds) {
        if (variantIds.isEmpty()) return List.of();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)
                .query(
                        "SELECT variant_id,seller_id,on_hand,reserved,version,updated_at FROM inventory_item WHERE variant_id IN (:variantIds)",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                                "variantIds", variantIds),
                        InventoryRepository::mapItem);
    }

    public List<Movement> movements(UUID sellerId, UUID variantId, int limit) {
        return jdbc.query(
                "SELECT id,variant_id,seller_id,movement_type,quantity_delta,reason_code,reference_id,actor_user_id,correlation_id,occurred_at FROM stock_movement WHERE seller_id=? AND variant_id=? ORDER BY occurred_at DESC LIMIT ?",
                InventoryRepository::mapMovement,
                sellerId,
                variantId,
                limit);
    }

    public boolean adjust(UUID variantId, int delta, long expectedVersion, Instant now) {
        return jdbc.update(
                        "UPDATE inventory_item SET on_hand=on_hand+?,version=version+1,updated_at=? WHERE variant_id=? AND version=? AND on_hand+?>=reserved",
                        delta,
                        db(now),
                        variantId,
                        expectedVersion,
                        delta)
                == 1;
    }

    public boolean reserve(UUID variantId, int quantity, Instant now) {
        return jdbc.update(
                        "UPDATE inventory_item SET reserved=reserved+?,version=version+1,updated_at=? WHERE variant_id=? AND on_hand-reserved>=?",
                        quantity,
                        db(now),
                        variantId,
                        quantity)
                == 1;
    }

    public void release(UUID variantId, int quantity, Instant now) {
        int changed =
                jdbc.update(
                        "UPDATE inventory_item SET reserved=reserved-?,version=version+1,updated_at=? WHERE variant_id=? AND reserved>=?",
                        quantity,
                        db(now),
                        variantId,
                        quantity);
        if (changed != 1)
            throw new IllegalStateException("Reservation invariant violated during release");
    }

    public void movement(
            UUID variantId,
            UUID sellerId,
            String type,
            int delta,
            String reason,
            UUID referenceId,
            UUID actor,
            String correlationId,
            Instant now) {
        jdbc.update(
                "INSERT INTO stock_movement(id,variant_id,seller_id,movement_type,quantity_delta,reason_code,reference_id,actor_user_id,correlation_id,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                variantId,
                sellerId,
                type,
                delta,
                reason,
                referenceId,
                actor,
                correlationId,
                db(now));
    }

    public Optional<Reservation> reservation(UUID referenceId) {
        return reservation(referenceId, false);
    }

    public Optional<Reservation> reservationForUpdate(UUID referenceId) {
        return reservation(referenceId, true);
    }

    private Optional<Reservation> reservation(UUID referenceId, boolean forUpdate) {
        return jdbc
                .query(
                        "SELECT id,reference_id,status,expires_at,created_at,updated_at FROM inventory_reservation WHERE reference_id=?"
                                + (forUpdate ? " FOR UPDATE" : ""),
                        (rs, row) ->
                                new Reservation(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("reference_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getObject("expires_at", java.time.OffsetDateTime.class)
                                                .toInstant(),
                                        rs.getObject("created_at", java.time.OffsetDateTime.class)
                                                .toInstant(),
                                        rs.getObject("updated_at", java.time.OffsetDateTime.class)
                                                .toInstant()),
                        referenceId)
                .stream()
                .findFirst();
    }

    public void commit(UUID variantId, int quantity, Instant now) {
        int changed =
                jdbc.update(
                        "UPDATE inventory_item SET on_hand=on_hand-?,reserved=reserved-?,version=version+1,updated_at=? WHERE variant_id=? AND on_hand>=? AND reserved>=?",
                        quantity,
                        quantity,
                        db(now),
                        variantId,
                        quantity,
                        quantity);
        if (changed != 1)
            throw new IllegalStateException("Reservation invariant violated during commitment");
    }

    public List<Reservation> expiredReservations(Instant now, int limit) {
        return jdbc.query(
                "SELECT id,reference_id,status,expires_at,created_at,updated_at FROM inventory_reservation WHERE status IN ('ACTIVE','PENDING') AND expires_at<=? ORDER BY expires_at LIMIT ?",
                (rs, row) ->
                        new Reservation(
                                rs.getObject("id", UUID.class),
                                rs.getObject("reference_id", UUID.class),
                                rs.getString("status"),
                                rs.getObject("expires_at", java.time.OffsetDateTime.class)
                                        .toInstant(),
                                rs.getObject("created_at", java.time.OffsetDateTime.class)
                                        .toInstant(),
                                rs.getObject("updated_at", java.time.OffsetDateTime.class)
                                        .toInstant()),
                db(now),
                limit);
    }

    public Reservation createReservation(UUID referenceId, Instant expiresAt, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_reservation(id,reference_id,status,expires_at,created_at,updated_at) VALUES (?,?,'PENDING',?,?,?)",
                id,
                referenceId,
                db(expiresAt),
                db(now),
                db(now));
        return reservation(referenceId).orElseThrow();
    }

    public void reservationLine(UUID reservationId, UUID variantId, int quantity) {
        jdbc.update(
                "INSERT INTO inventory_reservation_line(reservation_id,variant_id,quantity) VALUES (?,?,?)",
                reservationId,
                variantId,
                quantity);
    }

    public List<ReservationLine> reservationLines(UUID reservationId) {
        return jdbc.query(
                "SELECT reservation_id,variant_id,quantity FROM inventory_reservation_line WHERE reservation_id=? ORDER BY variant_id",
                (rs, row) ->
                        new ReservationLine(
                                rs.getObject("reservation_id", UUID.class),
                                rs.getObject("variant_id", UUID.class),
                                rs.getInt("quantity")),
                reservationId);
    }

    public boolean completeReservation(UUID id, String from, String to, Instant now) {
        return jdbc.update(
                        "UPDATE inventory_reservation SET status=?,updated_at=? WHERE id=? AND status=?",
                        to,
                        db(now),
                        id,
                        from)
                == 1;
    }

    public boolean completePendingReservation(UUID id, String to, Instant now) {
        return jdbc.update(
                        "UPDATE inventory_reservation SET status=?,updated_at=? WHERE id=? AND status IN ('ACTIVE','PENDING')",
                        to,
                        db(now),
                        id)
                == 1;
    }

    public void outbox(
            String eventType,
            UUID aggregateId,
            long version,
            String correlationId,
            Map<String, Object> data,
            Instant now) {
        outbox(eventType, "InventoryItem", aggregateId, version, correlationId, data, now);
    }

    public void reservationOutbox(
            String eventType,
            UUID reservationId,
            long version,
            String correlationId,
            Map<String, Object> data,
            Instant now) {
        outbox(eventType, "Reservation", reservationId, version, correlationId, data, now);
    }

    private void outbox(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            long version,
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
                        Map.entry("aggregateVersion", version),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "inventory-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                "INSERT INTO outbox_event(event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,payload,occurred_at,next_attempt_at) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?)",
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                version,
                correlationId,
                json(envelope),
                db(now),
                db(now));
    }

    public boolean processed(String consumer, UUID eventId) {
        return jdbc.update(
                        "INSERT INTO processed_message(consumer_name,event_id,processed_at) VALUES (?,?,now()) ON CONFLICT DO NOTHING",
                        consumer,
                        eventId)
                == 0;
    }

    public boolean claimIdempotency(String operation, String key, String requestHash) {
        return jdbc.update(
                        "INSERT INTO idempotency_record(operation,idempotency_key,request_hash,response_payload,created_at,expires_at) VALUES (?,?,?,'{}'::jsonb,now(),now()+interval '24 hours') ON CONFLICT DO NOTHING",
                        operation,
                        key,
                        requestHash)
                == 1;
    }

    public Optional<Idempotency> idempotency(String operation, String key) {
        return jdbc
                .query(
                        "SELECT request_hash,resource_id,resource_version FROM idempotency_record WHERE operation=? AND idempotency_key=?",
                        (rs, row) ->
                                new Idempotency(
                                        rs.getString("request_hash"),
                                        rs.getObject("resource_id", UUID.class),
                                        rs.getObject("resource_version", Long.class)),
                        operation,
                        key)
                .stream()
                .findFirst();
    }

    public void completeIdempotency(String operation, String key, UUID resourceId, long version) {
        jdbc.update(
                "UPDATE idempotency_record SET resource_id=?,resource_version=? WHERE operation=? AND idempotency_key=?",
                resourceId,
                version,
                operation,
                key);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize inventory event", exception);
        }
    }

    private static java.sql.Timestamp db(Instant value) {
        return java.sql.Timestamp.from(value);
    }

    private static Item mapItem(ResultSet rs, int row) throws SQLException {
        return new Item(
                rs.getObject("variant_id", UUID.class),
                rs.getObject("seller_id", UUID.class),
                rs.getInt("on_hand"),
                rs.getInt("reserved"),
                rs.getLong("version"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }

    private static Movement mapMovement(ResultSet rs, int row) throws SQLException {
        return new Movement(
                rs.getObject("id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getObject("seller_id", UUID.class),
                rs.getString("movement_type"),
                rs.getInt("quantity_delta"),
                rs.getString("reason_code"),
                rs.getObject("reference_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("correlation_id"),
                rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant());
    }

    public record Item(
            UUID variantId,
            UUID sellerId,
            int onHand,
            int reserved,
            long version,
            Instant updatedAt) {
        public int available() {
            return onHand - reserved;
        }
    }

    public record Movement(
            UUID id,
            UUID variantId,
            UUID sellerId,
            String type,
            int quantityDelta,
            String reasonCode,
            UUID referenceId,
            UUID actorUserId,
            String correlationId,
            Instant occurredAt) {}

    public record Reservation(
            UUID id,
            UUID referenceId,
            String status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record ReservationLine(UUID reservationId, UUID variantId, int quantity) {}

    public record Idempotency(String requestHash, UUID resourceId, Long resourceVersion) {}
}
