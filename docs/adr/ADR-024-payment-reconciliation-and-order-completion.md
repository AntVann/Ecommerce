# ADR-024: Payment reconciliation and order completion

Status: Accepted
Date: 2026-08-06
Owners: MarketFlow Architecture

## Context

Milestone 3 leaves orders with an expiring Inventory reservation. Payment providers can approve,
decline, fail deterministically, time out after receiving a request, or deliver callbacks more than
once. Treating an ambiguous timeout as a decline can release stock for an authorized order, while
blindly retrying with a new provider key can authorize twice.

## Decision

Payment is an independent bounded context with its own PostgreSQL database, aggregate, attempt
history, idempotency records, provider callback inbox, and transactional outbox. Only opaque fake
tokens are accepted. A provider anti-corruption layer normalizes simulator results to
`AUTHORIZED`, `DECLINED`, `FAILED`, or `UNKNOWN` and uses one stable provider idempotency key for
the lifetime of a logical authorization attempt.

Order remains the Saga coordinator. Customers initiate payment only for an owned
`INVENTORY_RESERVED` order. Order calls the protected Payment interface and then consumes Payment
events idempotently. Authorization requests carry no payment token on Kafka. On authorization,
Order requests Inventory confirmation and becomes `CONFIRMED` only after stock commitment is
confirmed. Decline or deterministic failure requests Inventory release and becomes
`PAYMENT_FAILED` only after release is confirmed. Ambiguous or contradictory outcomes preserve the
reservation and ultimately enter `MANUAL_REVIEW` when safe automatic recovery is exhausted.

Inventory adds idempotent reservation-level confirmation and release completion events. Confirming
a reservation atomically decrements both on-hand and reserved stock; released or expired
reservations cannot later be confirmed.

## Alternatives considered

Adding a mandatory payment token to the existing checkout contract would break Milestone 3
clients. Publishing the fake token in an Order event would expand its exposure. Treating timeout as
decline, using a new key for every retry, or confirming an order before committing Inventory would
all create unsafe terminal inconsistencies.

## Consequences

Payment initiation is a separate idempotent Order operation. The success path has an additional
Inventory confirmation round trip, and deterministic payment failures have an explicit release
compensation phase. At-least-once delivery is safe because callbacks, commands, and facts are
deduplicated by durable identifiers and guarded state transitions.

## Security implications

Real card data is rejected and never persisted. Fake tokens are accepted only at the protected
authorization boundary and are excluded from logs, traces, metrics, events, and responses.
Internal calls and callbacks are authenticated; callback signatures use constant-time comparison.
Customer ownership and seller membership/line ownership are enforced in the service layer.

## Operational implications

Metrics expose authorization outcomes, provider latency, unknown attempts, reconciliation,
duplicates, compensation, manual review, inbox duplication, and outbox lag. Operators reconcile an
unknown attempt with its existing provider key and never create another logical authorization.

## Migration / rollback

Payment starts with a new database. Order, Inventory, and Seller migrations are additive and
forward-only. New event schemas use v1 names on new topics or additive messages; existing Inventory
line events remain compatible. Rolling Payment back leaves non-terminal orders reserved until the
existing expiry or manual-review path handles them.
