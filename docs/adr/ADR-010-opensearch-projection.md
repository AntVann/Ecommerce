# ADR-010: OpenSearch as event-built search projection
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Marketplace search needs full text, facets, filters, and rebuildable denormalized reads.
## Decision
Use OpenSearch as a projection built from catalog and seller events; Catalog remains authoritative.
## Alternatives considered
PostgreSQL search reduces infrastructure but not the target search capabilities; direct dual writes lose consistency.
## Consequences
Search may lag or fail without blocking direct catalog reads or writes.
## Security implications
Documents expose only customer-safe fields and OpenSearch remains privately networked.
## Operational implications
Projection lag, indexing errors, aliases, snapshots, and rebuild procedures are required.
## Migration / rollback
Build new indexes beside old ones and switch aliases after verification.

