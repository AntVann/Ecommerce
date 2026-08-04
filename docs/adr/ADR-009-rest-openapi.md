# ADR-009: REST and OpenAPI for synchronous contracts
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Browser and immediate service interactions require stable synchronous semantics and generated types.
## Decision
Use JSON REST with OpenAPI 3.1, `/api/v1`, RFC 9457 problems, correlation, and explicit idempotency.
## Alternatives considered
GraphQL complicates authorization and caching for the MVP; RPC is not browser-oriented.
## Consequences
Contracts are reviewed and linted before implementation; domain models never depend on generated DTOs.
## Security implications
Request DTOs, bounds, ownership checks, and allow-listed sorting prevent mass assignment and injection.
## Operational implications
Every client has deadlines, safe retry policy, telemetry, and stable error handling.
## Migration / rollback
Breaking changes use a new API version or documented compatibility window.

