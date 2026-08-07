# ADR-023: Cart and checkout consistency boundaries

Status: Accepted
Date: 2026-08-06
Owners: MarketFlow Architecture

## Context

Carts are short-lived, high-write customer state, while checkout creates durable commercial and
inventory commitments. A cart's displayed price and availability can change before checkout, and
clients or brokers may retry every operation.

## Decision

Cart owns versioned, expiring Redis documents for guest and authenticated actors. Cart prices are
advisory. Order owns checkout idempotency, immutable order snapshots, Saga state, and its
transactional outbox in PostgreSQL. Checkout revalidates current Catalog prices, Seller status,
and Inventory availability through bounded internal APIs before persisting an order. Inventory
owns reservations and consumes `order.created.v1` idempotently; Order consumes the existing
per-line reservation outcome events through a durable inbox.

The initial Saga ends at `INVENTORY_RESERVED`, `CANCELLED`, or `MANUAL_REVIEW`. Payment,
confirmation, notification, and fulfillment transitions are deliberately absent.

## Alternatives considered

Persisting carts in Order would couple ephemeral and durable lifecycles. Trusting advisory cart
prices would allow stale commercial terms. A shared database or distributed transaction would
violate bounded-context ownership. Broker-only checkout responses would make synchronous retry
semantics ambiguous.

## Consequences

Checkout has explicit synchronous validation followed by asynchronous reservation. Duplicate
idempotency keys return the original result only when the request fingerprint matches. Event
delivery remains at-least-once while inbox/outbox constraints make business effects idempotent.

## Security implications

Opaque guest credentials are stored only as digests, guest mutations require CSRF protection,
authenticated ownership derives from validated Identity tokens, and internal APIs require the
service credential. Address and order snapshots are excluded from logs, metrics, and events.

## Operational implications

Redis dependency health and cart TTL behavior are observable. Order metrics expose checkout,
idempotency, Saga transitions, reservation failures, outbox lag, and inbox duplicates. Expired
reservations are released by Inventory and surface as explicit failure/compensation outcomes.

## Migration / rollback

Redis keys use a versioned namespace. Order and Inventory migrations are additive. Event schemas
are versioned and consumers tolerate duplicate delivery, allowing services to roll back without
changing published Milestone 2 contracts.
