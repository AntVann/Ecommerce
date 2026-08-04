# Milestone Completion Report

## Milestone

Milestone 1: Identity and Seller Onboarding, implemented on
`milestone/01-identity-seller`. No Milestone 2 capability is included.

## Summary

MarketFlow now has independently deployable Identity and Seller services. Identity provides
customer registration, email-verification state, Argon2id credentials, login, short-lived RS256
access tokens, rotating opaque refresh tokens, reuse detection, logout, disablement, Redis-backed
login limits, global roles, internal user/token-state checks, security audit events, and an outbox.
Seller provides applications, administrator review, rejection, suspension, owner-managed staff
roles, live principal checks, tenant ownership enforcement, audit history, and seller status events.

## Acceptance Criteria

| Criterion | Status | Evidence |
|---|---|---|
| Registration is normalized, unique, verified, and non-enumerating | Completed | Database uniqueness plus registration/verification integration tests |
| Passwords use the approved adaptive hash and raw credentials are not persisted | Completed | Argon2id encoder and digest-storage assertions |
| Login, access tokens, refresh rotation, logout, reuse detection, and invalidation work | Completed | Identity workflow and security integration tests |
| Disabled, locked, revoked, or stale principals are rejected | Completed | Live token-state validation and disablement tests |
| Login attempts are Redis-limited, audited, metered, and return `Retry-After` | Completed | API integration test and Prometheus smoke check |
| Global roles and service-layer authorization are enforced | Completed | Identity administrator check and Seller live principal verification |
| Cross-seller access is denied without leaking tenant existence | Completed | Seller cross-tenant integration test with persisted denial audit |
| Seller applications can be approved, rejected, and suspended by an administrator | Completed | Optimistic, idempotent transitions with actor/reason history and events |
| Only owners can add, change, or remove active seller staff | Completed | Membership service policy, active-user lookup, and integration tests |
| Security events, structured logs, metrics, health, and traces are available | Completed | Live Compose smoke checks and security dashboard |
| Database, OpenAPI, AsyncAPI, and event contracts are versioned and validated | Completed | Flyway migration tests and contract validators |

## Architecture and Design

- Identity and Seller are separate Maven/Spring Boot modules and own separate PostgreSQL databases.
- Cross-context identifiers are opaque UUIDs; neither service queries the other's database.
- Seller validates JWT issuer, audience, signature, and time locally, then performs a bounded live
  Identity check before sensitive operations. Seller authorization is re-evaluated in the
  application service against current membership and seller state.
- Browser refresh credentials use Secure, HttpOnly, SameSite cookies with double-submit CSRF.
- Business changes and integration events commit atomically through per-service outbox tables.
- ADR-021 records session, revocation, live-authorization, and trust-boundary decisions.

## Files Changed

- `services/identity-service/`: Identity application, security, persistence, migrations, tests,
  Docker image, and service documentation.
- `services/seller-service/`: Seller governance application, authorization, persistence,
  migrations, tests, Docker image, and service documentation.
- `contracts/`: Identity/Seller OpenAPI documents and five versioned event schemas/examples.
- `platform/observability/`: Prometheus targets and the MarketFlow Security Grafana dashboard.
- `docs/`: ADR-021, architecture/threat-model updates, and local operations runbooks.
- Root build, Compose, dependency automation, bootstrap, Make, and smoke-validation files.

## Database Migrations

Identity applies three ordered migrations for accounts/credentials/verification; refresh-token
families, token revocations, and role assignments; then security audit, outbox, inbox, and
idempotency records. Seller applies three ordered migrations for applications/status history;
memberships and seeded role permissions; then security audit, outbox, inbox, and idempotency
records. Testcontainers proves migration from an empty schema and incremental upgrade from V1.

## API Changes

- Public Identity: register, resend/confirm verification, login, refresh, logout.
- Administrative Identity: disable account.
- Internal Identity: one-time verification-token claim, access-token state, and user summary.
- Public Seller: submit application, retrieve an owned/authorized seller, manage staff.
- Administrative Seller: list applications, approve, reject, and suspend.
- Identity exposes a public JWKS document; all errors use stable RFC 9457-style problem codes.

The source contracts are `contracts/openapi/identity-service.yaml` and
`contracts/openapi/seller-service.yaml`.

## Event Changes

The shared AsyncAPI document now includes `marketflow.identity.events.v1` and
`marketflow.seller.events.v1`. Added and validated event types are:

- `identity.user-registered.v1`
- `identity.user-disabled.v1`
- `seller.seller-approved.v1`
- `seller.seller-rejected.v1`
- `seller.seller-suspended.v1`

Raw verification, access, and refresh tokens are never included in event payloads.

## Security

- Argon2id parameters follow the approved memory-hard profile; credentials and opaque tokens are
  stored only as hashes/digests.
- RS256 tokens include `kid`, issuer, audience, subject, roles, JTI, issue, and expiry claims.
- Refresh rotation is transactional; replay compromises and revokes the entire token family.
- Account disablement revokes current families and invalidates earlier access tokens.
- Redis limits use keyed privacy digests rather than email addresses or raw source identifiers.
- Coarse role checks are backed by live database roles, current account state, seller status,
  current membership, and tenant ownership checks.
- Security logs and audit records contain stable codes and identifiers, not passwords, emails, raw
  tokens, authorization headers, or free-text administrative reasons.
- Runtime containers use non-root users. CI retains Gitleaks, Trivy, CodeQL, and dependency review.

## Observability

Both services emit ECS JSON logs with validated correlation IDs and W3C/OTLP traces. Actuator
liveness/readiness and Prometheus endpoints are enabled. Milestone metrics cover authentication
failure, login throttling, token reuse, token refresh, authorization denial, and seller status
changes. Prometheus scrapes both services and Grafana provisions a security-focused dashboard.

## Tests Added

- Identity normalization and cryptographic token unit tests.
- Identity migration, end-to-end session, CSRF, RBAC, rate-limit, audit, and disablement tests.
- Seller request-hash unit test.
- Seller migration, application/review/suspension, staff, RBAC, cross-tenant, and audit tests.
- The full reactor executes 14 tests: 6 unit tests and 8 integration tests.

## Commands Executed

```text
git fetch origin --prune
git checkout main
git pull --ff-only origin main
git status
git log --oneline --decorate -15
mvn -B clean verify
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-contracts.ps1
docker compose config --quiet
docker compose up -d --build
docker compose ps
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-infra.ps1
docker compose stop/start redis and identity-service (controlled readiness failure drills)
trivy filesystem --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed
git diff --check
```

## Validation Results

- Maven reactor: passed for parent, sample, Identity, and Seller modules.
- Tests: 14 passed; zero failures, errors, or skips.
- Spotless, Checkstyle, SpotBugs, and JaCoCo report generation: passed.
- OpenAPI, AsyncAPI, and JSON event example validation: passed. Redocly reports only documented
  shared-component/local-server warnings.
- Compose configuration, image builds, service/database health, metrics targets, broker topics,
  traces, and structured logs: passed.
- Controlled dependency failures: Identity returned bounded HTTP 503 readiness when Redis was
  stopped; Seller returned bounded HTTP 503 readiness when Identity was stopped; both recovered.
- Trivy source dependency scan: passed with zero fixed high or critical vulnerabilities after
  upgrading the supported Spring Boot line and patched transitive dependency versions.
- Secret-pattern review and generated-file review: passed; example values are explicitly local-only.

## Git Information

- Base: `origin/main`
- Branch: `milestone/01-identity-seller`
- Push target: `origin/milestone/01-identity-seller`
- Commit style: logical Conventional Commits
- Pull request target: `main`; the pull request must not be merged as part of this milestone task.

## Known Limitations

- Milestone 1 is API-only; no storefront, seller portal, or administrator UI is included.
- Verification and seller-decision delivery stop at durable outbox events and the minimal internal
  delivery interface because notification delivery is outside this milestone.
- Compose uses local-only shared keys, placeholder database credentials, and an ephemeral signing
  key. Production KMS/key rotation and workload identity remain deployment work.
- Initial ADMIN assignment is an operator-controlled database action; there is intentionally no
  public administrator bootstrap or self-elevation endpoint.
- Seller suspension is represented and published, but catalog/search/order effects are deferred to
  the services that own those future capabilities.

## Exit Criteria

- [x] Milestone 0 was merged into and synchronized from `origin/main`.
- [x] Only Identity and Seller Onboarding scope was implemented.
- [x] Acceptance, authorization, migration, and security tests pass.
- [x] Contracts and event examples validate.
- [x] The complete Compose environment builds, starts, and passes smoke checks.
- [x] Documentation, runbooks, threat model, ADR, metrics, and dashboard are updated.
- [x] No secret, raw credential/token, generated build output, or unrelated feature is included.
- [x] The branch is ready to push and open for review without merging.

## Final Status

MILESTONE COMPLETE — READY TO PUSH FOR REVIEW
