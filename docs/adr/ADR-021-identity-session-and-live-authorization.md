# ADR-021: Identity sessions and live authorization state

- Status: Accepted
- Date: 2026-08-04

## Context

ADR-012 selects short-lived signed access tokens and rotating opaque refresh-token families. The
Seller service must also reject disabled users, explicitly logged-out tokens, stale global roles,
and memberships outside the requested seller without reading Identity persistence.

## Decision

The Identity service is the sole issuer of RS256 access tokens and publishes its current public
keys as a JSON Web Key Set. Access tokens contain only the subject, issuer, audience, issued and
expiry times, token identifier, and coarse global roles. They never contain email addresses,
seller identifiers, or seller permissions.

Each service validates token signature and standard claims locally. Before a sensitive operation,
the Seller service sends the already-validated subject, token identifier, and issued-at time to an
authenticated Identity internal endpoint. Identity confirms current account state, global roles,
and token revocation. The check fails closed. Seller membership and permissions remain exclusively
Seller-owned and are evaluated locally for every seller-scoped operation.

Opaque refresh tokens are random, stored only as SHA-256 digests, rotated atomically, and grouped
into families. Reuse revokes the complete family. Logout revokes its family and presented access
token identifier; account disablement revokes every family and all tokens issued before the
disablement timestamp.

Browser refresh and logout requests use a Secure, HttpOnly, SameSite cookie plus a double-submit
CSRF token. Signing keys, internal-service credentials, and rate-limit key material are external
configuration. Local development may use generated ephemeral signing keys and local-only example
credentials, but production startup must reject missing externally managed key material.

## Consequences

- Seller does not depend on Identity database tables or stale seller claims.
- Sensitive Seller writes depend on Identity availability and fail closed when it is unavailable.
- Short-lived access tokens keep ordinary verification local while revocation remains immediate
  for privileged operations.
- Key rotation is possible through overlapping JWKS entries identified by `kid`.
- A later gateway may perform the same coarse checks but cannot replace service-layer ownership
  enforcement.
