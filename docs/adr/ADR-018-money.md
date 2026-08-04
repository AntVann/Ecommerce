# ADR-018: Money representation and rounding policy
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Commercial calculations must not suffer binary floating-point errors or implicit currency rules.
## Decision
Use a Money value object containing `BigDecimal` and an ISO 4217 currency; serialize amount as a decimal string.
## Alternatives considered
Floating point is inaccurate; minor-unit integers alone do not encode differing currency scales without policy.
## Consequences
Scale, rounding, currency equality, allocation, and totals are centralized and unit-tested.
## Security implications
Server-side calculations remain authoritative and reject invalid scale, sign, or currency.
## Operational implications
Databases use numeric columns and metrics avoid high-cardinality monetary labels.
## Migration / rollback
Policy changes are versioned and never recalculate immutable order snapshots silently.

