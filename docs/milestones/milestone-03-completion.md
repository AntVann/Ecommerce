# Milestone Completion Report

## Milestone

Milestone 3: Cart and Checkout, implemented on `milestone/03-cart-checkout`. Completed payment
processing, payment capture, final order confirmation, fulfillment, notification delivery, and
Milestone 4 capability are not included.

## Summary

MarketFlow now supports versioned Redis carts for guests and authenticated customers and an
idempotent checkout that creates durable immutable Order snapshots. Checkout replaces advisory
cart values with current Catalog prices, confirms Seller approval and Inventory availability, and
starts an orchestrated Inventory-reservation Saga through a transactional outbox. Inventory and
Order consume events through durable deduplication boundaries.

## Acceptance Criteria

| Criterion | Status | Evidence |
|---|---|---|
| Guest and customer carts expire and remain actor-scoped | Completed | Versioned Redis namespace, digest-only guest keys, configurable P7D/P30D TTLs |
| Cart CRUD enforces quantity and line bounds | Completed | Cart application rules, ETags, API contract, unit and Redis tests |
| Guest merge is deterministic, bounded, atomic, and idempotent | Completed | Redis Lua merge and integration test |
| Cart prices are advisory and timestamped | Completed | Cart quote snapshot model and Catalog validation client |
| Checkout requires a customer and idempotency key | Completed | JWT customer enforcement, fingerprinted idempotency record, replay tests |
| True retries return one order and conflicting reuse is rejected | Completed | Database uniqueness, advisory cart lock, concurrency and retry tests |
| Price, Seller, Inventory, Cart version, and addresses are revalidated | Completed | Protected owner APIs, checkout gateways, validation tests |
| Orders preserve immutable money, product, seller, SKU, and address snapshots | Completed | Order migration, BigDecimal model, PostgreSQL integration test |
| Order creation and `OrderCreated` publication are atomic | Completed | Order transaction and service-owned outbox |
| Inventory reservation handling is atomic and idempotent | Completed | Existing conditional reservation path, Order-event inbox, duplicate tests |
| Saga outcomes are explicit and failure-aware | Completed | `PENDING`, `INVENTORY_RESERVED`, `CANCELLED`, `MANUAL_REVIEW` transitions and tests |
| Contracts, logs, metrics, traces, probes, and runbooks are available | Completed | Live contract and Compose smoke validation |

## Architecture and Design

- Cart is an independent Redis-backed bounded context on port 8086. Guest credentials are opaque;
  only SHA-256 digests participate in Redis keys. Redis Lua scripts provide compare-and-set and
  atomic guest-to-customer merge behavior.
- Order is an independent PostgreSQL-backed bounded context on port 8087. It owns idempotency,
  order/item/address snapshots, status history, Saga state, outbox, and Inventory-event inbox.
- Catalog, Seller, Cart, and Inventory expose protected checkout-specific interfaces. No service
  reads another context's database or shares its persistence entities.
- Inventory remains reservation authority and idempotently consumes `order.order-created.v1`.
  Existing Inventory outcome contracts remain compatible and are additively correlated to orders.
- ADR-023 records the advisory-cart/durable-checkout consistency boundary.

## Database Migrations

Order V1 creates customer order, item snapshot, status history, Saga, per-line Inventory progress,
idempotency, outbox, and processed-message tables. Exact money uses `NUMERIC(19,4)`, addresses use
immutable JSONB snapshots, and uniqueness guards both cart-version orders and idempotency keys.
Inventory V2 preserves ACTIVE/PENDING compatibility, establishes PENDING reservation semantics,
and adds an expiry-work index. The migration is additive and upgrade-tested from V1.

## API and Event Changes

- Cart: get, add/combine, update, remove, clear, merge, and protected checkout snapshot.
- Order: authenticated idempotent checkout and customer-owned order detail.
- Catalog: protected batch variant/product/current-price validation.
- Seller: protected batch current-status validation.
- Inventory: protected batch availability plus existing reserve/release operations.
- Events: new `order.order-created.v1` on `marketflow.order.events.v1`; existing Inventory reserved,
  failed, and released events gain optional correlation details without breaking consumers.

Source contracts are in `contracts/openapi`, `contracts/asyncapi`, and `contracts/events`.

## Security

- JWT issuer, audience, signature, and customer role are enforced for customer operations.
- Guest cart cookies use 256 bits of entropy, HttpOnly/SameSite controls, digest-only server keys,
  and double-submit CSRF protection for mutations.
- Internal interfaces fail closed using constant-time service-key comparison in local runtime.
- Checkout rejects stale cart versions, disabled customers, inactive variants, unapproved sellers,
  unavailable quantities, invalid addresses, invalid decimal prices, and mixed currencies.
- Logs and metrics exclude guest tokens, cookies, authorization values, addresses, and payloads.

## Observability

Cart and Order publish ECS structured logs, correlation IDs, W3C/OTLP traces, Prometheus metrics,
and dependency-aware liveness/readiness. Metrics cover cart mutations/conflicts, checkout results,
idempotent replays, Saga transitions, inbox duplicates, and outbox publication. Prometheus polls
all eight service targets; the smoke script now waits for each target to become healthy, removing
the former startup race.

## Tests and Validation

The reactor reports 43 tests with zero failures, errors, or skips. Milestone 3 coverage includes:

- Cart quantity/merge behavior, guest CSRF, Redis CAS, TTL-preserving storage, and atomic merge.
- Checkout replay and conflicting-key behavior, current-fact validation, and one-outbox creation.
- PostgreSQL concurrent idempotency claims proving one winner.
- Duplicate Order-event reservation and duplicate failure handling.
- Saga final-line success, duplicate Inventory messages, and reservation-failure cancellation.
- Protected Catalog, Seller, and Inventory interface authorization/mapping.
- Inventory V1-to-V2 migration, expiration, negative-stock, and concurrency invariants.

Commands executed include:

```text
git fetch origin --prune
git checkout main
git pull --ff-only origin main
git status
git checkout -b milestone/03-cart-checkout
.\mvnw.cmd -B clean verify
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-contracts.ps1
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-infra.ps1
git diff --check
secret-pattern and generated-file review
```

Validation results:

- Maven nine-module reactor: passed in 395 seconds; Spotless, Checkstyle, SpotBugs, JaCoCo, unit,
  integration, migration, concurrency, security, idempotency, and Saga checks passed.
- OpenAPI, AsyncAPI, and event schemas: passed with 28 non-blocking style warnings and no errors.
- All service images built; the dependency-aware Compose stack and every configured container
  reached healthy/completed state.
- Live smoke: eight service probes and Prometheus targets, seven PostgreSQL databases, Redis,
  Kafka topics, OpenSearch, Grafana, Tempo traces, and structured correlation logs passed.

## Git Information

- Base: `origin/main`
- Branch: `milestone/03-cart-checkout`
- Push target: `origin/milestone/03-cart-checkout`
- Pull request target: `main`; this milestone must not be merged as part of this task.

## Known Limitations

- GitHub's Dependency Review check fails before analysis because the repository Dependency Graph
  is disabled. Enable the graph in repository settings to activate that external review gate; the
  Java, contract, security, and Compose validations are separate checks.
- The milestone is API-only and has no storefront UI.
- Cart estimates can become stale by design; only the Order snapshot is commercially authoritative.
- The Saga intentionally stops after Inventory reservation. It neither authorizes payment nor
  confirms, fulfills, or notifies an order.
- Local service keys and credentials are development placeholders. Production workload identity,
  secret management, and private transport remain deployment work.

## Exit Criteria

- [x] Milestone 2 was merged into and synchronized from `origin/main` before work began.
- [x] Only Cart, Checkout, initial Order, and Inventory-reservation Saga scope was implemented.
- [x] Carts are expiring, actor-scoped, versioned, advisory, and concurrency-safe.
- [x] Checkout and event consumers are idempotent; duplicate tests pass.
- [x] Current price, Seller status, Inventory availability, addresses, and ownership are validated.
- [x] Order snapshots, state history, outbox, inbox, migrations, APIs, and events are durable.
- [x] Published Inventory contracts remain compatible and no service accesses another database.
- [x] Full tests, contracts, image builds, Compose startup, observability, and smoke checks pass.
- [x] Documentation, ADR, threat model, architecture, and runbooks are updated.
- [x] No payment completion, confirmation, fulfillment, notification, secret, or generated output is included.

## Final Status

MILESTONE COMPLETE - READY FOR REVIEW
