package com.marketflow.seller.api;

import com.marketflow.seller.application.PrincipalStateVerifier;
import com.marketflow.seller.application.SellerRepository;
import com.marketflow.seller.application.SellerService;
import com.marketflow.seller.infrastructure.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SellerController {

    private final SellerService sellers;

    public SellerController(SellerService sellers) {
        this.sellers = sellers;
    }

    @PostMapping("/api/v1/seller-applications")
    ResponseEntity<SellerResponse> apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SellerApplicationRequest request) {
        var seller =
                sellers.apply(
                        principal(jwt),
                        request.displayName(),
                        request.legalName(),
                        request.countryCode(),
                        correlationId());
        return ResponseEntity.created(URI.create("/api/v1/sellers/" + seller.id()))
                .eTag(etag(seller.version()))
                .body(response(seller));
    }

    @GetMapping("/api/v1/sellers/{sellerId}")
    ResponseEntity<SellerResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId) {
        var seller = sellers.get(principal(jwt), sellerId, correlationId());
        return ResponseEntity.ok().eTag(etag(seller.version())).body(response(seller));
    }

    @GetMapping("/api/v1/admin/seller-applications")
    List<SellerResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        return sellers.listApplications(principal(jwt), status, limit, correlationId()).stream()
                .map(SellerController::response)
                .toList();
    }

    @PostMapping("/api/v1/admin/sellers/{sellerId}/approve")
    ResponseEntity<SellerResponse> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey) {
        return changed(
                sellers.approve(
                        principal(jwt),
                        sellerId,
                        version(ifMatch),
                        idempotencyKey,
                        correlationId()));
    }

    @PostMapping("/api/v1/admin/sellers/{sellerId}/reject")
    ResponseEntity<SellerResponse> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return changed(
                sellers.reject(
                        principal(jwt),
                        sellerId,
                        version(ifMatch),
                        idempotencyKey,
                        request.reason(),
                        correlationId()));
    }

    @PostMapping("/api/v1/admin/sellers/{sellerId}/suspend")
    ResponseEntity<SellerResponse> suspend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return changed(
                sellers.suspend(
                        principal(jwt),
                        sellerId,
                        version(ifMatch),
                        idempotencyKey,
                        request.reason(),
                        correlationId()));
    }

    @PostMapping("/api/v1/sellers/{sellerId}/members")
    ResponseEntity<Void> addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody AddMemberRequest request) {
        var membership =
                sellers.addMember(
                        principal(jwt),
                        sellerId,
                        request.userId(),
                        request.role(),
                        idempotencyKey,
                        correlationId());
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/sellers/" + sellerId + "/members/" + membership.userId()))
                .eTag(etag(membership.version()))
                .build();
    }

    @PatchMapping("/api/v1/sellers/{sellerId}/members/{userId}")
    ResponseEntity<Void> changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID userId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeRoleRequest request) {
        sellers.changeMemberRole(
                principal(jwt),
                sellerId,
                userId,
                request.role(),
                version(ifMatch),
                correlationId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/sellers/{sellerId}/members/{userId}")
    ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID userId,
            @RequestHeader("If-Match") String ifMatch) {
        sellers.removeMember(principal(jwt), sellerId, userId, version(ifMatch), correlationId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SellerResponse> changed(SellerRepository.SellerRecord seller) {
        return ResponseEntity.ok().eTag(etag(seller.version())).body(response(seller));
    }

    private static SellerResponse response(SellerRepository.SellerRecord seller) {
        return new SellerResponse(
                seller.id(), seller.displayName(), seller.status(), seller.version());
    }

    private PrincipalStateVerifier.PrincipalToken principal(Jwt jwt) {
        return new PrincipalStateVerifier.PrincipalToken(
                UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()), jwt.getIssuedAt());
    }

    private long version(String ifMatch) {
        String value = ifMatch.replace("W/", "").replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ETAG_400",
                    "If-Match must contain a valid resource version.");
        }
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? UUID.randomUUID().toString() : value;
    }

    record SellerApplicationRequest(
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotBlank @Size(min = 2, max = 200) String legalName,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {}

    record ReasonRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    record AddMemberRequest(
            @NotNull UUID userId, @NotBlank @Pattern(regexp = "MANAGER|STAFF") String role) {}

    record ChangeRoleRequest(@NotBlank @Pattern(regexp = "MANAGER|STAFF") String role) {}

    record SellerResponse(UUID sellerId, String displayName, String status, long version) {}
}
