# ADR-002: Evolutionary service extraction and bounded contexts
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Premature service extraction increases integration risk while a modular monolith can obscure ownership.
## Decision
Retain explicit bounded contexts and extract deployables only for scaling, isolation, security, ownership, or release cadence.
## Alternatives considered
A single undifferentiated application weakens boundaries; creating every possible service immediately increases failure modes.
## Consequences
Delivery uses runnable vertical slices and the service inventory in the engineering plan.
## Security implications
Security boundaries are explicit even when deployment cadence is initially shared.
## Operational implications
Every extracted service must justify its runtime and data-store cost.
## Migration / rollback
Extraction requires contract tests and controlled data migration; reversal preserves the bounded-context API.
