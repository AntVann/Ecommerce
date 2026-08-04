# ADR-005: Kafka for durable domain events
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Multiple consumers need durable, replayable, ordered domain facts.
## Decision
Use Kafka for immutable domain events, partitioned by aggregate ID when ordering matters.
## Alternatives considered
RabbitMQ fan-out is less suited to long-lived replay; synchronous callbacks couple availability.
## Consequences
Events are versioned, minimal, schema-validated, and safe under duplicate delivery.
## Security implications
Broker ACLs, TLS, payload minimization, and secret-free DLQs are mandatory outside local development.
## Operational implications
Consumer lag, throughput, retries, DLQs, and retention are observable.
## Migration / rollback
New event versions run in parallel during migration; old meanings are never reinterpreted.
