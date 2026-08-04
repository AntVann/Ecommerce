package com.marketflow.identity.api;

import com.marketflow.identity.application.IdentityService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public final class InternalIdentityController {

    private final IdentityService identity;

    public InternalIdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/email-verifications/{verificationId}/token")
    VerificationDelivery issueVerification(
            @PathVariable UUID verificationId,
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String internalKey) {
        requireInternalKey(internalKey);
        var delivery = identity.issueVerificationToken(verificationId);
        return new VerificationDelivery(
                delivery.verificationId(), delivery.token(), delivery.expiresAt());
    }

    @PostMapping("/auth/token-status")
    TokenStatusResponse tokenStatus(
            @Valid @RequestBody TokenStatusRequest request,
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String internalKey) {
        requireInternalKey(internalKey);
        var status = identity.tokenStatus(request.subject(), request.tokenId(), request.issuedAt());
        return new TokenStatusResponse(status.active(), status.roles());
    }

    @GetMapping("/users/{userId}")
    UserSummaryResponse userSummary(
            @PathVariable UUID userId,
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String internalKey) {
        requireInternalKey(internalKey);
        var summary = identity.userSummary(userId);
        return new UserSummaryResponse(summary.userId(), summary.active(), summary.roles());
    }

    private void requireInternalKey(String supplied) {
        if (!identity.validInternalKey(supplied)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_AUTHENTICATION_401",
                    "Internal authentication failed.");
        }
    }

    record VerificationDelivery(UUID verificationId, String token, Instant expiresAt) {}

    record TokenStatusRequest(UUID subject, UUID tokenId, Instant issuedAt) {}

    record TokenStatusResponse(boolean active, List<String> roles) {}

    record UserSummaryResponse(UUID userId, boolean active, List<String> roles) {}
}
