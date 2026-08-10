package com.marketflow.seller.application;

import com.marketflow.seller.api.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SellerService.class);
    private final SellerRepository repository;
    private final PrincipalStateVerifier principalVerifier;
    private final Clock clock;
    private final Counter authorizationDenials;
    private final Counter sellerStatusChanges;

    @org.springframework.beans.factory.annotation.Autowired
    public SellerService(
            SellerRepository repository,
            PrincipalStateVerifier principalVerifier,
            MeterRegistry registry) {
        this(repository, principalVerifier, registry, Clock.systemUTC());
    }

    SellerService(
            SellerRepository repository,
            PrincipalStateVerifier principalVerifier,
            MeterRegistry registry,
            Clock clock) {
        this.repository = repository;
        this.principalVerifier = principalVerifier;
        this.clock = clock;
        this.authorizationDenials = registry.counter("authorization.denied.total");
        this.sellerStatusChanges = registry.counter("seller.status.changed.total");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.SellerRecord apply(
            PrincipalStateVerifier.PrincipalToken principal,
            String displayName,
            String legalName,
            String countryCode,
            String correlationId) {
        principalVerifier.verify(principal, correlationId);
        Instant now = clock.instant();
        try {
            UUID sellerId =
                    repository.createApplication(
                            principal.userId(),
                            displayName.trim(),
                            legalName.trim(),
                            countryCode,
                            correlationId,
                            now);
            repository.audit(
                    "SELLER_APPLICATION",
                    principal.userId(),
                    sellerId,
                    "SUCCESS",
                    "APPLICATION_SUBMITTED",
                    correlationId,
                    now);
            return requiredSeller(sellerId, false);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELLER_APPLICATION_CONFLICT_409",
                    "An active seller application already exists.");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.SellerRecord get(
            PrincipalStateVerifier.PrincipalToken principal, UUID sellerId, String correlationId) {
        var state = principalVerifier.verify(principal, correlationId);
        var seller = requiredSeller(sellerId, false);
        if (!state.hasRole("ADMIN")
                && !seller.applicantUserId().equals(principal.userId())
                && !repository.hasPermission(sellerId, principal.userId(), "SELLER_PROFILE_READ")) {
            denied(principal.userId(), sellerId, "SELLER_OWNERSHIP_REQUIRED", correlationId);
            throw notFound();
        }
        return seller;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public List<SellerRepository.SellerRecord> listApplications(
            PrincipalStateVerifier.PrincipalToken principal,
            String status,
            int limit,
            String correlationId) {
        requireAdmin(principal, correlationId);
        return repository.list(status, limit);
    }

    @Transactional(readOnly = true)
    public List<SellerRepository.SecurityEvent> securityEvents(
            PrincipalStateVerifier.PrincipalToken principal,
            int limit,
            int offset,
            String correlationId) {
        requireAdmin(principal, correlationId);
        return repository.securityEvents(limit, offset);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.SellerRecord approve(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            long expectedVersion,
            String idempotencyKey,
            String correlationId) {
        requireAdmin(principal, correlationId);
        var seller =
                transition(
                        principal,
                        sellerId,
                        expectedVersion,
                        idempotencyKey,
                        "APPROVE_SELLER",
                        "PENDING_REVIEW",
                        "APPROVED",
                        "ADMIN_APPROVED",
                        "seller.seller-approved.v1",
                        Map.of("sellerId", sellerId),
                        correlationId);
        repository.addOwner(sellerId, seller.applicantUserId(), clock.instant());
        return seller;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.SellerRecord reject(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            String correlationId) {
        requireAdmin(principal, correlationId);
        return transition(
                principal,
                sellerId,
                expectedVersion,
                idempotencyKey,
                "REJECT_SELLER",
                "PENDING_REVIEW",
                "REJECTED",
                reason,
                "seller.seller-rejected.v1",
                Map.of("sellerId", sellerId, "reasonCode", "ADMIN_REJECTED"),
                correlationId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.SellerRecord suspend(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            String correlationId) {
        requireAdmin(principal, correlationId);
        return transition(
                principal,
                sellerId,
                expectedVersion,
                idempotencyKey,
                "SUSPEND_SELLER",
                "APPROVED",
                "SUSPENDED",
                reason,
                "seller.seller-suspended.v1",
                Map.of("sellerId", sellerId, "reasonCode", "ADMIN_SUSPENDED"),
                correlationId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SellerRepository.Membership addMember(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            UUID userId,
            String role,
            String idempotencyKey,
            String correlationId) {
        principalVerifier.verify(principal, correlationId);
        requireOwner(principal.userId(), sellerId, correlationId);
        requireApproved(sellerId);
        if (!principalVerifier.userState(userId, correlationId).active()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SELLER_MEMBER_ACCOUNT_INACTIVE_422",
                    "The member account is not active.");
        }
        String requestHash = digest(sellerId + ":" + userId + ":" + role);
        var prior = repository.idempotency("ADD_SELLER_MEMBER", idempotencyKey);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(requestHash)) {
                throw idempotencyConflict();
            }
            return repository.membership(sellerId, userId).orElseThrow(this::notFound);
        }
        try {
            repository.addMember(sellerId, userId, role, clock.instant());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELLER_MEMBERSHIP_CONFLICT_409",
                    "Seller membership already exists.");
        }
        var membership = repository.membership(sellerId, userId).orElseThrow(this::notFound);
        repository.saveIdempotency(
                "ADD_SELLER_MEMBER",
                idempotencyKey,
                requestHash,
                membership.id(),
                membership.version(),
                clock.instant());
        repository.audit(
                "SELLER_MEMBER_ADD",
                principal.userId(),
                sellerId,
                "SUCCESS",
                "MEMBER_ADDED",
                correlationId,
                clock.instant());
        return membership;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changeMemberRole(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            UUID userId,
            String role,
            long expectedVersion,
            String correlationId) {
        principalVerifier.verify(principal, correlationId);
        requireOwner(principal.userId(), sellerId, correlationId);
        requireApproved(sellerId);
        if (!repository.changeMemberRole(
                sellerId, userId, role, expectedVersion, clock.instant())) {
            throw precondition();
        }
        repository.audit(
                "SELLER_MEMBER_ROLE_CHANGE",
                principal.userId(),
                sellerId,
                "SUCCESS",
                "MEMBER_ROLE_CHANGED",
                correlationId,
                clock.instant());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void removeMember(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            UUID userId,
            long expectedVersion,
            String correlationId) {
        principalVerifier.verify(principal, correlationId);
        requireOwner(principal.userId(), sellerId, correlationId);
        requireApproved(sellerId);
        var existing = repository.membership(sellerId, userId);
        if (existing.isEmpty()) {
            return;
        }
        if ("OWNER".equals(existing.get().role())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELLER_OWNER_REMOVAL_409",
                    "The seller owner cannot be removed.");
        }
        if (!repository.removeMember(sellerId, userId, expectedVersion)) {
            throw precondition();
        }
        repository.audit(
                "SELLER_MEMBER_REMOVE",
                principal.userId(),
                sellerId,
                "SUCCESS",
                "MEMBER_REMOVED",
                correlationId,
                clock.instant());
    }

    private SellerRepository.SellerRecord transition(
            PrincipalStateVerifier.PrincipalToken principal,
            UUID sellerId,
            long expectedVersion,
            String idempotencyKey,
            String operation,
            String requiredState,
            String nextState,
            String reason,
            String eventType,
            Map<String, Object> eventData,
            String correlationId) {
        String requestHash =
                digest(sellerId + ":" + expectedVersion + ":" + nextState + ":" + reason);
        var prior = repository.idempotency(operation, idempotencyKey);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(requestHash)) {
                throw idempotencyConflict();
            }
            return requiredSeller(prior.get().resourceId(), false);
        }
        var before = requiredSeller(sellerId, true);
        if (!requiredState.equals(before.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELLER_STATE_CONFLICT_409",
                    "Seller state does not permit this operation.");
        }
        Instant now = clock.instant();
        if (!repository.transition(
                sellerId,
                expectedVersion,
                nextState,
                principal.userId(),
                reason,
                correlationId,
                now)) {
            throw precondition();
        }
        var changed = requiredSeller(sellerId, false);
        repository.saveIdempotency(
                operation, idempotencyKey, requestHash, sellerId, changed.version(), now);
        Map<String, Object> completeData = new java.util.HashMap<>(eventData);
        if ("APPROVED".equals(nextState)) {
            completeData.put("ownerUserId", changed.applicantUserId());
        }
        repository.outbox(eventType, sellerId, changed.version(), correlationId, completeData, now);
        repository.audit(
                operation, principal.userId(), sellerId, "SUCCESS", nextState, correlationId, now);
        sellerStatusChanges.increment();
        LOGGER.atInfo()
                .addKeyValue("operation", operation)
                .addKeyValue("actor.id", principal.userId())
                .addKeyValue("seller.id", sellerId)
                .addKeyValue("seller.status", nextState)
                .log("Seller state changed");
        return changed;
    }

    private void requireAdmin(
            PrincipalStateVerifier.PrincipalToken principal, String correlationId) {
        var state = principalVerifier.verify(principal, correlationId);
        if (!state.hasRole("ADMIN")) {
            denied(principal.userId(), null, "ADMIN_ROLE_REQUIRED", correlationId);
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTHORIZATION_DENIED_403",
                    "This operation is not permitted.");
        }
    }

    private void requireOwner(UUID actorId, UUID sellerId, String correlationId) {
        var membership = repository.membership(sellerId, actorId);
        if (membership.isEmpty() || !"OWNER".equals(membership.get().role())) {
            denied(actorId, sellerId, "SELLER_OWNER_REQUIRED", correlationId);
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTHORIZATION_DENIED_403",
                    "This operation is not permitted.");
        }
    }

    private void requireApproved(UUID sellerId) {
        if (!"APPROVED".equals(requiredSeller(sellerId, false).status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELLER_STATE_CONFLICT_409",
                    "Seller must be approved for this operation.");
        }
    }

    private void denied(UUID actor, UUID sellerId, String reason, String correlationId) {
        repository.audit(
                "AUTHORIZATION_DENIED",
                actor,
                sellerId,
                "DENIED",
                reason,
                correlationId,
                clock.instant());
        LOGGER.atWarn()
                .addKeyValue("security.event", "authorization.denied")
                .addKeyValue("actor.id", actor)
                .addKeyValue("seller.id", sellerId)
                .addKeyValue("reason", reason)
                .addKeyValue("correlationId", correlationId)
                .log("Seller authorization denied");
        authorizationDenials.increment();
    }

    private SellerRepository.SellerRecord requiredSeller(UUID sellerId, boolean forUpdate) {
        return repository.findSeller(sellerId, forUpdate).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND_404", "Seller was not found.");
    }

    private ApiException precondition() {
        return new ApiException(
                HttpStatus.PRECONDITION_FAILED,
                "SELLER_VERSION_MISMATCH_412",
                "The resource version has changed.");
    }

    private ApiException idempotencyConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED_409",
                "The idempotency key was already used for a different request.");
    }

    static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
