# ADR-017: UUID identifiers and UTC time
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Independent services need globally unique opaque IDs and consistent time semantics.
## Decision
Use UUIDs, preferring UUIDv7 for generated IDs, and store/emit timezone-aware UTC timestamps.
## Alternatives considered
Database sequences leak ordering and coordinate ownership; local times create ambiguous ordering.
## Consequences
Contracts serialize IDs as UUID strings and timestamps as ISO 8601 UTC.
## Security implications
Opaque IDs do not replace ownership authorization and must not expose sensitive meaning.
## Operational implications
Hosts synchronize clocks and traces retain event, correlation, and causation identifiers.
## Migration / rollback
UUID variants remain contract-compatible; timestamp changes use additive migration.

