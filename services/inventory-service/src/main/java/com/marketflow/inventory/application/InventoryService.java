package com.marketflow.inventory.application;

import com.marketflow.inventory.api.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository repository;
    private final SellerAuthorizer authorizer;
    private final MeterRegistry metrics;
    private final Clock clock;

    @Autowired
    public InventoryService(
            InventoryRepository repository, SellerAuthorizer authorizer, MeterRegistry metrics) {
        this(repository, authorizer, metrics, Clock.systemUTC());
    }

    InventoryService(
            InventoryRepository repository,
            SellerAuthorizer authorizer,
            MeterRegistry metrics,
            Clock clock) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.metrics = metrics;
        this.clock = clock;
    }

    public List<InventoryRepository.Item> list(UUID userId, UUID sellerId, String correlationId) {
        authorizer.require(sellerId, userId, correlationId);
        return repository.sellerItems(sellerId);
    }

    public List<InventoryRepository.Movement> movements(
            UUID userId, UUID sellerId, UUID variantId, int limit, String correlationId) {
        authorizer.require(sellerId, userId, correlationId);
        owned(variantId, sellerId);
        return repository.movements(sellerId, variantId, Math.min(limit, 100));
    }

    public List<InventoryRepository.Item> availability(List<UUID> variantIds) {
        return repository.items(variantIds.stream().distinct().toList());
    }

    @Transactional
    public InventoryRepository.Item adjust(
            UUID userId,
            UUID sellerId,
            UUID variantId,
            int delta,
            long version,
            String reason,
            String idempotencyKey,
            String correlationId) {
        authorizer.require(sellerId, userId, correlationId);
        owned(variantId, sellerId);
        String requestHash = hash(variantId + ":" + delta + ":" + version + ":" + reason);
        if (!repository.claimIdempotency("inventory-adjustment", idempotencyKey, requestHash)) {
            var prior =
                    repository.idempotency("inventory-adjustment", idempotencyKey).orElseThrow();
            if (!prior.requestHash().equals(requestHash)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED_409",
                        "Idempotency key was reused with a different request.");
            }
            return repository.item(variantId).orElseThrow();
        }
        Instant now = Instant.now(clock);
        if (!repository.adjust(variantId, delta, version, now)) {
            metrics.counter("inventory_contention_total", "operation", "adjust").increment();
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVENTORY_ADJUSTMENT_CONFLICT_409",
                    "Stock adjustment would violate availability or the version is stale.");
        }
        repository.movement(
                variantId, sellerId, "ADJUSTMENT", delta, reason, null, userId, correlationId, now);
        var changed = repository.item(variantId).orElseThrow();
        event(
                "inventory.inventory-adjusted.v1",
                changed,
                correlationId,
                Map.of("delta", delta, "reasonCode", reason));
        metrics.counter("inventory_adjustment_total").increment();
        repository.completeIdempotency(
                "inventory-adjustment", idempotencyKey, changed.variantId(), changed.version());
        return changed;
    }

    @Transactional
    public InventoryRepository.Reservation reserve(
            UUID referenceId, List<ReserveLine> lines, Duration ttl, String correlationId) {
        return reserveInternal(referenceId, lines, ttl, correlationId);
    }

    @Transactional
    public void reserveOrderEvent(
            UUID eventId,
            UUID orderId,
            List<ReserveLine> lines,
            Duration ttl,
            String correlationId) {
        if (repository.processed("inventory-order-v1", eventId)) return;
        reserveInternal(orderId, lines, ttl, correlationId);
    }

    private InventoryRepository.Reservation reserveInternal(
            UUID referenceId, List<ReserveLine> lines, Duration ttl, String correlationId) {
        var existing = repository.reservation(referenceId);
        if (existing.isPresent()) return existing.get();
        Instant now = Instant.now(clock);
        var reservation = repository.createReservation(referenceId, now.plus(ttl), now);
        for (ReserveLine line :
                lines.stream().sorted(Comparator.comparing(ReserveLine::variantId)).toList()) {
            var item = repository.item(line.variantId()).orElseThrow(InventoryService::notFound);
            if (!repository.reserve(line.variantId(), line.quantity(), now)) {
                metrics.counter("inventory_contention_total", "operation", "reserve").increment();
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_INSUFFICIENT_409",
                        "Insufficient inventory is available.");
            }
            repository.reservationLine(reservation.id(), line.variantId(), line.quantity());
            repository.movement(
                    line.variantId(),
                    item.sellerId(),
                    "RESERVATION",
                    -line.quantity(),
                    "RESERVATION_CREATED",
                    referenceId,
                    null,
                    correlationId,
                    now);
            var changed = repository.item(line.variantId()).orElseThrow();
            event(
                    "inventory.inventory-reserved.v1",
                    changed,
                    correlationId,
                    Map.of(
                            "referenceId",
                            referenceId,
                            "orderId",
                            referenceId,
                            "reservationId",
                            reservation.id(),
                            "quantity",
                            line.quantity()));
        }
        return reservation;
    }

    @Transactional
    public InventoryRepository.Reservation release(UUID referenceId, String correlationId) {
        var reservation =
                repository.reservation(referenceId).orElseThrow(InventoryService::notFound);
        if (!("ACTIVE".equals(reservation.status()) || "PENDING".equals(reservation.status())))
            return reservation;
        Instant now = Instant.now(clock);
        for (var line : repository.reservationLines(reservation.id())) {
            var item = repository.item(line.variantId()).orElseThrow();
            repository.release(line.variantId(), line.quantity(), now);
            repository.movement(
                    line.variantId(),
                    item.sellerId(),
                    "RELEASE",
                    line.quantity(),
                    "RESERVATION_RELEASED",
                    referenceId,
                    null,
                    correlationId,
                    now);
            var changed = repository.item(line.variantId()).orElseThrow();
            event(
                    "inventory.inventory-released.v1",
                    changed,
                    correlationId,
                    Map.of("referenceId", referenceId, "quantity", line.quantity()));
        }
        repository.completePendingReservation(reservation.id(), "RELEASED", now);
        return repository.reservation(referenceId).orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReservationFailure(
            UUID referenceId, String reasonCode, String correlationId) {
        repository.outbox(
                "inventory.inventory-reservation-failed.v1",
                referenceId,
                1,
                correlationId,
                Map.of("referenceId", referenceId, "reasonCode", reasonCode),
                Instant.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOrderReservationFailure(
            UUID eventId, UUID referenceId, String reasonCode, String correlationId) {
        if (repository.processed("inventory-order-v1", eventId)) return;
        repository.outbox(
                "inventory.inventory-reservation-failed.v1",
                referenceId,
                1,
                correlationId,
                Map.of("referenceId", referenceId, "reasonCode", reasonCode),
                Instant.now(clock));
    }

    @Transactional
    public void expire(InventoryRepository.Reservation reservation) {
        if (!repository.completePendingReservation(reservation.id(), "EXPIRED", Instant.now(clock)))
            return;
        Instant now = Instant.now(clock);
        for (var line : repository.reservationLines(reservation.id())) {
            var item = repository.item(line.variantId()).orElseThrow();
            repository.release(line.variantId(), line.quantity(), now);
            repository.movement(
                    line.variantId(),
                    item.sellerId(),
                    "EXPIRATION",
                    line.quantity(),
                    "RESERVATION_EXPIRED",
                    reservation.referenceId(),
                    null,
                    "reservation-expiry",
                    now);
            var changed = repository.item(line.variantId()).orElseThrow();
            event(
                    "inventory.inventory-released.v1",
                    changed,
                    "reservation-expiry",
                    Map.of(
                            "referenceId",
                            reservation.referenceId(),
                            "quantity",
                            line.quantity(),
                            "reasonCode",
                            "EXPIRED"));
        }
    }

    public List<InventoryRepository.Reservation> expiredReservations() {
        return repository.expiredReservations(Instant.now(clock), 50);
    }

    @Transactional
    public void ensureItem(UUID variantId, UUID sellerId) {
        repository.ensureItem(variantId, sellerId, Instant.now(clock));
    }

    private InventoryRepository.Item owned(UUID variantId, UUID sellerId) {
        return repository
                .item(variantId)
                .filter(i -> i.sellerId().equals(sellerId))
                .orElseThrow(InventoryService::notFound);
    }

    private void event(
            String type,
            InventoryRepository.Item item,
            String correlationId,
            Map<String, Object> details) {
        repository.outbox(
                type,
                item.variantId(),
                item.version(),
                correlationId,
                Map.of(
                        "variantId",
                        item.variantId(),
                        "sellerId",
                        item.sellerId(),
                        "onHand",
                        item.onHand(),
                        "reserved",
                        item.reserved(),
                        "available",
                        item.available(),
                        "details",
                        details),
                Instant.now(clock));
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "INVENTORY_RESOURCE_NOT_FOUND_404",
                "Inventory resource was not found.");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record ReserveLine(UUID variantId, int quantity) {
        public ReserveLine {
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        }
    }
}
