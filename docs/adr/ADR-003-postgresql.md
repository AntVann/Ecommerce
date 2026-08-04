# ADR-003: PostgreSQL as primary relational database
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Transactional aggregates, constraints, commercial values, and migration tooling require a robust relational store.
## Decision
Use PostgreSQL for service-owned relational persistence.
## Alternatives considered
MySQL is viable but not the approved baseline; document stores weaken relational invariants for core write models.
## Consequences
Services use Flyway or Liquibase, numeric money columns, UTC timestamps, and tested indexes.
## Security implications
Connections require TLS outside local development and least-privilege service roles.
## Operational implications
Backups, point-in-time recovery, pool metrics, and restore tests are required.
## Migration / rollback
Use expand-and-contract migrations and forward recovery for irreversible changes.
