package com.marketflow.identity.api;

import com.marketflow.identity.application.IdentityService;
import com.marketflow.identity.infrastructure.security.IdentitySecurityProperties;
import com.marketflow.identity.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class IdentityController {

    static final String REFRESH_COOKIE = "MF_REFRESH";
    static final String CSRF_COOKIE = "MF_CSRF";
    private final IdentityService identity;
    private final IdentitySecurityProperties properties;

    public IdentityController(IdentityService identity, IdentitySecurityProperties properties) {
        this.identity = identity;
        this.properties = properties;
    }

    @PostMapping("/auth/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        identity.register(request.email(), request.password(), correlationId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/email-verifications/resend")
    ResponseEntity<Void> resend(@Valid @RequestBody EmailRequest request) {
        identity.resendVerification(request.email(), correlationId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/email-verifications/confirm")
    ResponseEntity<Void> confirm(@Valid @RequestBody VerificationRequest request) {
        identity.confirmVerification(request.verificationId(), request.token(), correlationId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/login")
    ResponseEntity<AccessTokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        var result =
                identity.login(
                        request.email(),
                        request.password(),
                        servletRequest.getRemoteAddr(),
                        correlationId());
        return authenticated(result);
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @CookieValue(name = CSRF_COOKIE, required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfHeader) {
        requireCsrf(csrfCookie, csrfHeader);
        if (refreshToken == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_401", "Session refresh failed.");
        }
        return authenticated(identity.refresh(refreshToken, correlationId()));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @CookieValue(name = CSRF_COOKIE, required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfHeader) {
        requireCsrf(csrfCookie, csrfHeader);
        identity.logout(
                refreshToken,
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getId()),
                jwt.getExpiresAt(),
                correlationId());
        ResponseCookie expiredRefresh = cookie(REFRESH_COOKIE, "", true, Duration.ZERO);
        ResponseCookie expiredCsrf = cookie(CSRF_COOKIE, "", false, Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .header(HttpHeaders.SET_COOKIE, expiredCsrf.toString())
                .build();
    }

    @PostMapping("/admin/users/{userId}/disable")
    ResponseEntity<Void> disable(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        identity.disable(userId, UUID.fromString(jwt.getSubject()), correlationId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AccessTokenResponse> authenticated(
            IdentityService.AuthenticationResult result) {
        long expiresIn =
                Duration.between(Instant.now(), result.accessToken().expiresAt()).toSeconds();
        ResponseCookie refresh =
                cookie(
                        REFRESH_COOKIE,
                        result.refreshToken(),
                        true,
                        properties.refreshAbsoluteTtl());
        ResponseCookie csrf =
                cookie(CSRF_COOKIE, result.csrfToken(), false, properties.refreshAbsoluteTtl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refresh.toString())
                .header(HttpHeaders.SET_COOKIE, csrf.toString())
                .body(new AccessTokenResponse(result.accessToken().value(), "Bearer", expiresIn));
    }

    private ResponseCookie cookie(String name, String value, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private void requireCsrf(String cookie, String header) {
        if (cookie == null
                || header == null
                || !com.marketflow.identity.application.SecretTokens.equal(cookie, header)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "AUTH_CSRF_INVALID_403", "CSRF validation failed.");
        }
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? UUID.randomUUID().toString() : value;
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 256) String password) {}

    record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {}

    record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 256) String password) {}

    record VerificationRequest(UUID verificationId, @NotBlank @Size(max = 256) String token) {}

    record ReasonRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
