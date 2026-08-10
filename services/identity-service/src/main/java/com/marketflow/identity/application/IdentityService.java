package com.marketflow.identity.application;

import com.marketflow.identity.api.ApiException;
import com.marketflow.identity.infrastructure.security.IdentitySecurityProperties;
import com.marketflow.identity.infrastructure.security.JwtTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityService.class);
    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokens;
    private final LoginRateLimiter rateLimiter;
    private final IdentitySecurityProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;
    private final Counter authenticationFailures;
    private final Counter tokenReuseDetected;
    private final Counter tokenRefreshes;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokens,
            LoginRateLimiter rateLimiter,
            IdentitySecurityProperties properties,
            MeterRegistry registry) {
        this(
                repository,
                passwordEncoder,
                jwtTokens,
                rateLimiter,
                properties,
                registry,
                Clock.systemUTC());
    }

    public List<IdentityRepository.SecurityEvent> securityEvents(int limit, int offset) {
        return repository.securityEvents(Math.min(limit, 200), Math.max(offset, 0));
    }

    IdentityService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokens,
            LoginRateLimiter rateLimiter,
            IdentitySecurityProperties properties,
            MeterRegistry registry,
            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokens = jwtTokens;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(SecretTokens.random());
        this.authenticationFailures = registry.counter("authentication.failure.total");
        this.tokenReuseDetected = registry.counter("token.reuse.detected.total");
        this.tokenRefreshes = registry.counter("token.refresh.total");
    }

    @Transactional
    public void register(String email, String password, String correlationId) {
        String normalized = normalizeEmail(email);
        if (repository.findAccountByEmail(normalized, false).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(password);
        try {
            repository.insertAccount(
                    userId, email.trim(), normalized, passwordHash, verificationId, now);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        repository.audit(
                "USER_REGISTERED",
                userId,
                userId,
                "SUCCESS",
                "REGISTRATION_ACCEPTED",
                correlationId,
                null,
                now);
        repository.outbox(
                "identity.user-registered.v1",
                "UserAccount",
                userId,
                1,
                correlationId,
                Map.of("userId", userId, "verificationId", verificationId),
                now);
        LOGGER.atInfo()
                .addKeyValue("operation", "identity.register")
                .addKeyValue("actor.id", userId)
                .log("Customer registration accepted");
    }

    @Transactional
    public void resendVerification(String email, String correlationId) {
        String normalized = normalizeEmail(email);
        repository
                .findAccountByEmail(normalized, true)
                .ifPresent(
                        account -> {
                            if (!"PENDING_VERIFICATION".equals(account.status())) {
                                return;
                            }
                            Instant now = clock.instant();
                            UUID verificationId = UUID.randomUUID();
                            repository.cancelVerifications(account.id());
                            repository.insertVerification(verificationId, account.id(), now);
                            long aggregateVersion =
                                    repository.incrementAccountVersion(account.id(), now);
                            repository.outbox(
                                    "identity.user-registered.v1",
                                    "UserAccount",
                                    account.id(),
                                    aggregateVersion,
                                    correlationId,
                                    Map.of(
                                            "userId",
                                            account.id(),
                                            "verificationId",
                                            verificationId),
                                    now);
                        });
    }

    @Transactional
    public VerificationDelivery issueVerificationToken(UUID verificationId) {
        var verification =
                repository
                        .lockVerification(verificationId)
                        .orElseThrow(() -> notFound("Verification request was not found."));
        if (!"QUEUED".equals(verification.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_VERIFICATION_STATE_409",
                    "Verification delivery was already claimed.");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.verificationTtl());
        String rawToken = SecretTokens.random();
        repository.issueVerification(verificationId, SecretTokens.digest(rawToken), now, expiresAt);
        return new VerificationDelivery(verificationId, rawToken, expiresAt);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirmVerification(UUID verificationId, String token, String correlationId) {
        var verification =
                repository
                        .lockVerification(verificationId)
                        .orElseThrow(() -> invalidVerification());
        Instant now = clock.instant();
        String suppliedDigest = SecretTokens.digest(token);
        if (!"ISSUED".equals(verification.status())
                || verification.expiresAt() == null
                || !verification.expiresAt().isAfter(now)
                || verification.tokenDigest() == null
                || !SecretTokens.equal(verification.tokenDigest(), suppliedDigest)) {
            repository.audit(
                    "EMAIL_VERIFICATION",
                    null,
                    verification.userId(),
                    "DENIED",
                    "INVALID_OR_EXPIRED",
                    correlationId,
                    null,
                    now);
            throw invalidVerification();
        }
        repository.consumeVerification(verificationId, verification.userId(), now);
        repository.audit(
                "EMAIL_VERIFICATION",
                verification.userId(),
                verification.userId(),
                "SUCCESS",
                "EMAIL_VERIFIED",
                correlationId,
                null,
                now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticationResult login(
            String email, String password, String sourceAddress, String correlationId) {
        String normalized = normalizeEmail(email);
        rateLimiter.check(normalized, sourceAddress);
        Instant now = clock.instant();
        var account = repository.findAccountByEmail(normalized, true).orElse(null);
        boolean passwordMatches =
                account == null
                        ? passwordEncoder.matches(password, dummyPasswordHash)
                        : passwordEncoder.matches(password, account.passwordHash());
        boolean active = account != null && "ACTIVE".equals(account.status());
        boolean unlocked =
                account != null
                        && (account.lockedUntil() == null || !account.lockedUntil().isAfter(now));
        if (!passwordMatches || !active || !unlocked) {
            if (account != null && active) {
                repository.recordLoginFailure(account.id(), now);
            }
            repository.audit(
                    "AUTHENTICATION",
                    account == null ? null : account.id(),
                    account == null ? null : account.id(),
                    "DENIED",
                    "INVALID_CREDENTIALS_OR_STATE",
                    correlationId,
                    SecretTokens.digest(properties.rateLimitKey() + ':' + sourceAddress),
                    now);
            LOGGER.atWarn()
                    .addKeyValue("security.event", "authentication.denied")
                    .addKeyValue("subject.id", account == null ? null : account.id())
                    .addKeyValue("reason", "INVALID_CREDENTIALS_OR_STATE")
                    .addKeyValue("correlationId", correlationId)
                    .log("Authentication denied");
            authenticationFailures.increment();
            throw invalidCredentials();
        }
        repository.resetLoginFailures(account.id(), now);
        if (passwordEncoder.upgradeEncoding(account.passwordHash())) {
            repository.updatePasswordHash(account.id(), passwordEncoder.encode(password), now);
        }
        List<String> roles = repository.roles(account.id());
        var access = jwtTokens.issue(account.id(), roles);
        UUID familyId = UUID.randomUUID();
        UUID refreshId = UUID.randomUUID();
        String refreshToken = SecretTokens.random();
        String csrfToken = SecretTokens.random();
        repository.createSession(
                familyId,
                refreshId,
                account.id(),
                SecretTokens.digest(refreshToken),
                now,
                now.plus(properties.refreshIdleTtl()),
                now.plus(properties.refreshAbsoluteTtl()));
        repository.audit(
                "AUTHENTICATION",
                account.id(),
                account.id(),
                "SUCCESS",
                "AUTHENTICATED",
                correlationId,
                null,
                now);
        return new AuthenticationResult(access, refreshToken, csrfToken);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticationResult refresh(String rawRefreshToken, String correlationId) {
        Instant now = clock.instant();
        var session =
                repository
                        .lockRefreshToken(SecretTokens.digest(rawRefreshToken))
                        .orElseThrow(() -> invalidRefresh());
        if (!"CURRENT".equals(session.tokenStatus())) {
            repository.revokeFamily(session.familyId(), "COMPROMISED", "TOKEN_REUSE", now);
            repository.audit(
                    "TOKEN_REUSE",
                    session.userId(),
                    session.userId(),
                    "DENIED",
                    "REFRESH_TOKEN_REUSED",
                    correlationId,
                    null,
                    now);
            LOGGER.atWarn()
                    .addKeyValue("security.event", "refresh_token.reuse")
                    .addKeyValue("subject.id", session.userId())
                    .addKeyValue("token.family.id", session.familyId())
                    .addKeyValue("correlationId", correlationId)
                    .log("Refresh token reuse detected");
            tokenReuseDetected.increment();
            throw invalidRefresh();
        }
        if (!"ACTIVE".equals(session.familyStatus())
                || !"ACTIVE".equals(session.accountStatus())
                || !session.expiresAt().isAfter(now)
                || !session.absoluteExpiresAt().isAfter(now)) {
            repository.revokeFamily(session.familyId(), "REVOKED", "SESSION_EXPIRED", now);
            throw invalidRefresh();
        }
        UUID replacementId = UUID.randomUUID();
        String replacement = SecretTokens.random();
        Instant idleExpiry = now.plus(properties.refreshIdleTtl());
        Instant expiry =
                idleExpiry.isBefore(session.absoluteExpiresAt())
                        ? idleExpiry
                        : session.absoluteExpiresAt();
        repository.rotateRefreshToken(
                session, replacementId, SecretTokens.digest(replacement), now, expiry);
        var access = jwtTokens.issue(session.userId(), repository.roles(session.userId()));
        tokenRefreshes.increment();
        return new AuthenticationResult(access, replacement, SecretTokens.random());
    }

    @Transactional
    public void logout(
            String rawRefreshToken,
            UUID userId,
            UUID tokenId,
            Instant accessExpiry,
            String correlationId) {
        Instant now = clock.instant();
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            repository
                    .lockRefreshToken(SecretTokens.digest(rawRefreshToken))
                    .filter(session -> session.userId().equals(userId))
                    .ifPresent(
                            session ->
                                    repository.revokeFamily(
                                            session.familyId(), "REVOKED", "LOGOUT", now));
        }
        repository.revokeAccessToken(tokenId, userId, accessExpiry, "LOGOUT", now);
        repository.audit(
                "LOGOUT", userId, userId, "SUCCESS", "SESSION_REVOKED", correlationId, null, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void disable(UUID userId, UUID adminId, String correlationId) {
        Instant now = clock.instant();
        var administrator = repository.findAccountById(adminId, false).orElse(null);
        if (administrator == null
                || !"ACTIVE".equals(administrator.status())
                || !repository.roles(adminId).contains("ADMIN")) {
            repository.audit(
                    "ACCOUNT_DISABLE",
                    adminId,
                    userId,
                    "DENIED",
                    "ADMIN_ROLE_REQUIRED",
                    correlationId,
                    null,
                    now);
            LOGGER.atWarn()
                    .addKeyValue("security.event", "authorization.denied")
                    .addKeyValue("actor.id", adminId)
                    .addKeyValue("subject.id", userId)
                    .addKeyValue("reason", "ADMIN_ROLE_REQUIRED")
                    .addKeyValue("correlationId", correlationId)
                    .log("Account disable denied");
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTHORIZATION_DENIED_403",
                    "This operation is not permitted.");
        }
        var account = repository.findAccountById(userId, true).orElse(null);
        if (account == null) {
            repository.audit(
                    "ACCOUNT_DISABLE",
                    adminId,
                    userId,
                    "DENIED",
                    "TARGET_NOT_FOUND",
                    correlationId,
                    null,
                    now);
            throw notFound("User account was not found.");
        }
        if (repository.disableAccount(userId, now)) {
            repository.revokeAllSessions(userId, "ACCOUNT_DISABLED", now);
            repository.outbox(
                    "identity.user-disabled.v1",
                    "UserAccount",
                    userId,
                    account.version() + 1,
                    correlationId,
                    Map.of("userId", userId, "reasonCode", "ADMIN_DISABLED"),
                    now);
            LOGGER.atInfo()
                    .addKeyValue("security.event", "account.disabled")
                    .addKeyValue("actor.id", adminId)
                    .addKeyValue("subject.id", userId)
                    .addKeyValue("correlationId", correlationId)
                    .log("Account disabled");
        }
        repository.audit(
                "ACCOUNT_DISABLE",
                adminId,
                userId,
                "SUCCESS",
                "ADMIN_DISABLED",
                correlationId,
                null,
                now);
    }

    @Transactional(readOnly = true)
    public TokenStatus tokenStatus(UUID userId, UUID tokenId, Instant issuedAt) {
        Instant now = clock.instant();
        var account = repository.findAccountById(userId, false).orElse(null);
        boolean active =
                account != null
                        && "ACTIVE".equals(account.status())
                        && !repository.isAccessTokenRevoked(tokenId, now)
                        && (account.tokenInvalidBefore() == null
                                || issuedAt.isAfter(account.tokenInvalidBefore()));
        return new TokenStatus(active, active ? repository.roles(userId) : List.of());
    }

    @Transactional(readOnly = true)
    public UserSummary userSummary(UUID userId) {
        var account = repository.findAccountById(userId, false).orElse(null);
        boolean active = account != null && "ACTIVE".equals(account.status());
        return new UserSummary(userId, active, active ? repository.roles(userId) : List.of());
    }

    public boolean validInternalKey(String supplied) {
        return supplied != null && SecretTokens.equal(properties.internalServiceKey(), supplied);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS_401", "Authentication failed.");
    }

    private ApiException invalidRefresh() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_401", "Session refresh failed.");
    }

    private ApiException invalidVerification() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMAIL_VERIFICATION_INVALID_400",
                "Verification token is invalid or expired.");
    }

    private ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "IDENTITY_RESOURCE_NOT_FOUND_404", detail);
    }

    public record VerificationDelivery(UUID verificationId, String token, Instant expiresAt) {}

    public record AuthenticationResult(
            JwtTokenService.IssuedAccessToken accessToken, String refreshToken, String csrfToken) {}

    public record TokenStatus(boolean active, List<String> roles) {}

    public record UserSummary(UUID userId, boolean active, List<String> roles) {}
}
