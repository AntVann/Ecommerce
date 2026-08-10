package com.marketflow.inventory.api;

import com.marketflow.inventory.application.InventoryRepository;
import com.marketflow.inventory.application.InventoryService;
import com.marketflow.inventory.infrastructure.security.InventorySecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class InventoryController {
    private final InventoryService inventory;
    private final InventorySecurityProperties properties;

    public InventoryController(InventoryService inventory, InventorySecurityProperties properties) {
        this.inventory = inventory;
        this.properties = properties;
    }

    @GetMapping("/api/v1/variants/{variantId}/availability")
    InventoryRepository.PublicAvailability publicAvailability(@PathVariable UUID variantId) {
        return inventory.publicAvailability(variantId);
    }

    @GetMapping("/api/v1/sellers/{sellerId}/inventory")
    List<InventoryRepository.Item> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId) {
        return inventory.list(UUID.fromString(jwt.getSubject()), sellerId, correlation());
    }

    @PostMapping("/api/v1/sellers/{sellerId}/inventory/{variantId}/adjustments")
    ResponseEntity<InventoryRepository.Item> adjust(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID variantId,
            @RequestHeader("If-Match") String match,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String key,
            @Valid @RequestBody AdjustmentRequest request) {
        var item =
                inventory.adjust(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        variantId,
                        request.quantityDelta(),
                        version(match),
                        request.reasonCode(),
                        key,
                        correlation());
        return ResponseEntity.ok().eTag(etag(item.version())).body(item);
    }

    @GetMapping("/api/v1/sellers/{sellerId}/inventory/{variantId}/movements")
    List<InventoryRepository.Movement> movements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return inventory.movements(
                UUID.fromString(jwt.getSubject()), sellerId, variantId, limit, correlation());
    }

    @PostMapping("/internal/v1/inventory/reservations")
    InventoryRepository.Reservation reserve(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @Valid @RequestBody ReservationRequest request) {
        requireKey(key);
        try {
            return inventory.reserve(
                    request.referenceId(),
                    request.lines().stream()
                            .map(
                                    line ->
                                            new InventoryService.ReserveLine(
                                                    line.variantId(), line.quantity()))
                            .toList(),
                    Duration.ofSeconds(request.ttlSeconds()),
                    correlation());
        } catch (ApiException exception) {
            if ("INVENTORY_INSUFFICIENT_409".equals(exception.code())) {
                inventory.recordReservationFailure(
                        request.referenceId(), "INSUFFICIENT_AVAILABLE", correlation());
            }
            throw exception;
        }
    }

    @PostMapping("/internal/v1/inventory/availability")
    List<InventoryRepository.Item> availability(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @Valid @RequestBody AvailabilityRequest request) {
        requireKey(key);
        return inventory.availability(request.variantIds());
    }

    @PostMapping("/internal/v1/inventory/reservations/{referenceId}/release")
    InventoryRepository.Reservation release(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @PathVariable UUID referenceId) {
        requireKey(key);
        return inventory.release(referenceId, correlation());
    }

    @PostMapping("/internal/v1/inventory/reservations/{referenceId}/confirm")
    InventoryRepository.Reservation confirm(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @PathVariable UUID referenceId) {
        requireKey(key);
        return inventory.confirm(referenceId, correlation());
    }

    private void requireKey(String supplied) {
        byte[] expected = properties.internalServiceKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual))
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_AUTHENTICATION_401",
                    "Internal authentication failed.");
    }

    private static long version(String match) {
        try {
            return Long.parseLong(match.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_VALIDATION_400",
                    "If-Match must contain a numeric version.");
        }
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static String correlation() {
        String id = MDC.get("correlationId");
        return id == null ? "unknown" : id;
    }

    public record AdjustmentRequest(
            int quantityDelta, @NotBlank @Size(max = 80) String reasonCode) {}

    public record ReservationLineRequest(@NotNull UUID variantId, @Min(1) int quantity) {}

    public record ReservationRequest(
            @NotNull UUID referenceId,
            @NotEmpty @Size(max = 100) List<@Valid ReservationLineRequest> lines,
            @Min(60) @Max(3600) long ttlSeconds) {}

    public record AvailabilityRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> variantIds) {}
}
