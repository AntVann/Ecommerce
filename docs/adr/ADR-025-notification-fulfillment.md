# ADR-025: Notification task delivery and Order-owned fulfillment

Status: Accepted for Milestone 5
Date: 2026-08-09
Owners: MarketFlow Architecture

## Context

Milestone 5 must deliver order and shipment notifications without making order correctness
depend on provider availability. Sellers also need to fulfill only the order lines they own.

## Decision

- Kafka remains the durable source of order facts.
- Notification jobs are persisted in the Notification database and dispatched as task commands
  through durable RabbitMQ queues with bounded retry queues and a dead-letter queue.
- Notification consumers and task workers are idempotent; delivery attempts and template versions
  are persisted.
- The fake email adapter is the only provider implementation. Real credentials and card or carrier
  data are prohibited.
- MVP fulfillment remains inside the Order bounded context. Shipment mutations require live
  seller-membership permission checks and seller-scoped repository predicates.

## Consequences

Notification delivery is eventually consistent and can be retried or dead-lettered without
changing a confirmed order. Shipment state and order-line ownership remain transactionally
consistent in the Order database. No service accesses another service's database.

## Retry and recovery

Transient provider failures use bounded exponential backoff with jitter. Invalid or terminal
failures are routed to a named DLQ. Redrive is an audited, idempotent operator action after the
failure cause is corrected.

## Security and operations

Queues use least-privilege credentials and a private virtual host. Messages and telemetry contain
only allow-listed template data and identifiers. Queue depth, oldest message age, retry count,
delivery outcome, and DLQ size are monitored.
