# ADR-011: Redis for carts and distributed rate limits
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Carts and gateway rate limits need low-latency expiring state shared by replicas.
## Decision
Use Redis with environment- and actor-scoped keys and explicit TTLs.
## Alternatives considered
In-memory caches fail across replicas; PostgreSQL is durable but less suitable for these ephemeral access patterns.
## Consequences
Cart prices remain advisory and Redis failure has documented degraded behavior.
## Security implications
Private TLS connections, authentication, key scoping, and fail-closed security limits are required.
## Operational implications
Latency, memory, eviction, hit rate, and availability are monitored.
## Migration / rollback
Namespaces and serializers are versioned; carts can expire or be migrated without affecting orders.
