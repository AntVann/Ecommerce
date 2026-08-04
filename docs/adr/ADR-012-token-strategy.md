# ADR-012: JWT access tokens and rotating opaque refresh tokens
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Browser and service APIs need short-lived authorization with revocable long-lived sessions.
## Decision
Use signed short-lived JWT access tokens and hashed, rotating opaque refresh-token families.
## Alternatives considered
Long-lived JWTs are hard to revoke; persistent browser bearer tokens increase theft exposure.
## Consequences
Reuse revokes a token family, account state gates refresh, and browser refresh tokens use secure cookies.
## Security implications
Issuer, audience, expiry, signature, CSRF, rotation, rate limits, and safe logging are mandatory.
## Operational implications
Key rotation, token reuse, failed login, and denied authorization are observable.
## Migration / rollback
Verification keys overlap during rotation; claim changes remain backward compatible.
