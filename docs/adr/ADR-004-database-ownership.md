# ADR-004: Database ownership per service
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Shared schemas create hidden coupling and bypass domain invariants.
## Decision
Each service exclusively owns its database/schema, credentials, entities, migrations, and backup classification.
## Alternatives considered
Shared schemas and cross-service joins offer short-term convenience but prevent independent evolution.
## Consequences
Cross-context references are opaque IDs and projections are rebuilt from contracts.
## Security implications
Database roles cannot access another service's data.
## Operational implications
Health, capacity, migrations, and recovery are tracked per owner.
## Migration / rollback
Boundary changes require an ADR, export/import contract, reconciliation, and rollback plan.
