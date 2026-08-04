# MarketFlow Contracts

This directory contains transport contracts, not domain or persistence models. Contract precedence
is defined by `docs/engineering-plan.md`: accepted ADRs, then these versioned schemas, then the
engineering plan, then code.

## Conventions

- External REST uses `/api/v1`; non-routable service operations use `/internal/v1`.
- Errors use RFC 9457 problem details plus stable `code` and `correlationId` fields.
- IDs use UUID strings. Timestamps are ISO 8601 UTC. Money is a decimal string and ISO 4217 code.
- `X-Correlation-ID` is accepted and returned; W3C `traceparent` is propagated.
- Events use immutable past-tense names and the `domain.entity-event.v1` convention.
- Consumers must tolerate unknown fields and deduplicate by event ID and consumer name.
- Breaking REST or event changes require a new version and a migration plan.

Run `scripts/validate-contracts.sh` or `scripts/validate-contracts.ps1` from the repository root.
