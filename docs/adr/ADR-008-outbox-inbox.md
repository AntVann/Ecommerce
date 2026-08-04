# ADR-008: Transactional outbox and inbox deduplication
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Database commits and broker acknowledgements cannot be made atomic across systems.
## Decision
Producers persist events with aggregates in an outbox; consumers persist deduplication with business effects.
## Alternatives considered
Direct publish after commit loses events; assuming exactly-once networking is incorrect.
## Consequences
Business outcomes are exactly-once through idempotency while delivery remains at-least-once.
## Security implications
Outbox and inbox payloads follow the same minimization and access controls as domain data.
## Operational implications
Oldest unpublished age, attempts, lag, and replay are observable and runbook-driven.
## Migration / rollback
Schema changes are expand-and-contract; relays and consumers tolerate mixed compatible versions.

