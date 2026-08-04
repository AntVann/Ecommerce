# Milestone Completion Report

## Milestone

Milestone 2: Catalog and Inventory, implemented on
`milestone/02-catalog-inventory`. No cart, checkout, payment, order completion, notification,
fulfillment, or Milestone 3 capability is included.

## Summary

MarketFlow now has independently deployable Catalog, Inventory, and Search services. Catalog owns
products, variants, categories, attributes, image metadata, prices, lifecycle transitions, and
publication rules. Inventory owns stock, movements, availability, optimistic concurrency, atomic
adjustments, and reservation foundations. Search maintains a disposable OpenSearch projection from
versioned catalog and seller events and supports zero-downtime index rebuilds.

## Acceptance Criteria

| Criterion | Status | Evidence |
|---|---|---|
| Products, variants, categories, attributes, images, and prices are represented | Completed | Catalog migration, domain model, API, and migration tests |
| Seller-scoped SKU uniqueness and ownership authorization are enforced | Completed | Database unique constraint and live Seller permission checks |
| Publication validates required content, active variants, ready images, and seller state | Completed | Transactional lifecycle service and publication-failure test |
| Money uses `BigDecimal` with currency and scale validation | Completed | Money value object and precision tests |
| Stock adjustments are atomic, versioned, and cannot make stock negative | Completed | Conditional SQL updates, database constraints, and concurrency test |
| Movement history and availability are queryable | Completed | Immutable movement ledger and Inventory API |
| Reservation foundations are deterministic, atomic, expiring, and failure-aware | Completed | Sorted multi-line reservation workflow, release/expiry, and failure outbox events |
| Catalog and Inventory changes publish versioned events transactionally | Completed | Service-owned outboxes, schemas, AsyncAPI, and publisher tests/build validation |
| Event consumers are idempotent and tolerate duplicate delivery | Completed | Per-consumer inbox tables and unique processed-message keys |
| Search excludes non-active and suspended-seller products | Completed | Projection guards, seller status projection, and out-of-order version test |
| Search can rebuild into a new index and atomically switch its alias | Completed | Versioned rebuild jobs, protected catalog export, and restart-safe alias bootstrap |
| Logs, metrics, traces, readiness, and dependency health are available | Completed | Live Compose smoke validation and Prometheus targets |

## Architecture and Design

- Catalog, Inventory, and Search are separate Maven/Spring Boot modules with separate PostgreSQL
  databases and migrations. No service queries another service's database or imports its
  persistence model.
- Catalog and Inventory authorize seller operations through the Seller service's protected
  internal permission endpoint. Authorization is re-evaluated at the application-service boundary.
- Integration uses Kafka and a transactional outbox. Consumers record event IDs in service-owned
  inbox tables before committing their local projection changes.
- OpenSearch is explicitly non-authoritative. Product detail remains in Catalog, stock remains in
  Inventory, and the search index can be rebuilt from the protected Catalog export.
- ADR-022 records the supported Spring Boot 4.1 / Java 21 compilation baseline while runtime images
  use the current JDK 25 runtime.

## Files Changed

- `services/catalog-service/`: catalog application, security, persistence, migration, messaging,
  tests, Docker image, and service documentation.
- `services/inventory-service/`: inventory and reservation application, conditional persistence,
  migration, messaging, tests, Docker image, and service documentation.
- `services/search-service/`: idempotent Kafka projection, seller-state handling, OpenSearch health,
  alias rebuild, operational migration, tests, Docker image, and documentation.
- `services/seller-service/`: protected seller authorization endpoint and Catalog/Inventory
  permission migration.
- `contracts/`: Catalog, Inventory, and Search OpenAPI documents plus versioned Catalog/Inventory
  event schemas and AsyncAPI channels.
- `docker-compose.yml`, Prometheus configuration, Docker build inputs, and smoke scripts.
- Architecture, threat-model, ADR, root README, and local runbook documentation.

## Database Migrations

Catalog creates category, product, variant, image, lifecycle history, outbox, inbox, and seller
projection tables. Seller-scoped canonical SKU uniqueness and exact `NUMERIC(19,4)` price storage
are database-enforced. Inventory creates item, movement, reservation, reservation-line, outbox,
inbox, and idempotency tables with non-negative and reserved-not-greater-than-on-hand constraints.
Search creates only operational processed-message, rebuild-job, and seller-state projection tables;
documents remain disposable in OpenSearch. Seller migration V4 extends existing staff permissions
without changing published contracts.

## API Changes

- Catalog: category listing; seller product creation/update; variants; images; price changes;
  publish, deactivate, and archive transitions; public active product detail; protected rebuild
  export.
- Inventory: seller inventory and movement queries; idempotent adjustments with `If-Match`;
  availability; protected reserve and release operations.
- Search: public product search and a protected administrative rebuild operation.
- Seller: protected live seller/user/permission authorization for service-to-service checks.

The source contracts are in `contracts/openapi/` and preserve the shared correlation,
idempotency, optimistic-concurrency, money, and problem-detail conventions.

## Event Changes

The shared AsyncAPI document includes Catalog and Inventory channels. Added event types are:

- `catalog.product-published.v1`
- `catalog.product-updated.v1`
- `catalog.product-deactivated.v1`
- `catalog.price-changed.v1`
- `inventory.inventory-adjusted.v1`
- `inventory.inventory-reserved.v1`
- `inventory.inventory-reservation-failed.v1`
- `inventory.inventory-released.v1`

All events use the shared envelope, immutable event IDs, aggregate versions, UTC timestamps, and
correlation IDs. Consumers use inbox deduplication, and seller projections reject stale aggregate
versions.

## Security

- JWT issuer, audience, signature, and lifetime are validated locally for seller-facing APIs.
- Seller identity, status, staff permission, and ownership are checked live by the owning Seller
  context; internal endpoints use constant-time shared-key comparison and fail closed.
- Public Catalog returns only active products for approved sellers. Search deletes inactive and
  suspended-seller products and refuses to project drafts.
- Conditional writes require resource versions, adjustment requests are idempotency-bound, and
  multi-line reservations use a deterministic lock order.
- Logs exclude authorization headers, service keys, raw tokens, and credentials. The review found
  no committed private keys, GitHub tokens, cloud keys, or generated build artifacts.

## Observability

Catalog, Inventory, and Search emit ECS JSON logs with validated correlation IDs and W3C/OTLP
traces. Each exposes liveness, readiness, Prometheus metrics, and dependency-aware health. Metrics
cover product transitions, authorization failures, inventory adjustments/reservations, outbox
publication, projection work, and search rebuild outcomes. Prometheus successfully scrapes all
three services in the local stack.

## Tests Added

- Catalog money precision/scale tests.
- Catalog publication-validation and fail-closed Seller authorization tests.
- Catalog migration tests for exact decimal storage and seller-scoped SKU uniqueness.
- Inventory PostgreSQL concurrency test proving two simultaneous final-unit writes cannot both
  succeed and stock cannot become negative.
- Search migration/version test proving stale seller status events cannot overwrite newer state.

The full reactor executes 21 tests: seven Milestone 2 tests plus the existing Milestone 0 and 1
suite. All passed with zero failures, errors, or skips.

## Commands Executed

```text
git fetch origin --prune
git checkout main
git pull --ff-only origin main
git status
git log --oneline --decorate -15
mvn clean verify
docker run ... node:22-alpine npm run lint
docker compose config --quiet
docker compose up -d --build --wait search-service
docker compose ps
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-infra.ps1
git diff --check
secret-pattern and generated-file review
```

## Validation Results

- Maven reactor: passed for all seven modules in 195 seconds.
- Tests: 21 passed; zero failures, errors, or skips. Inventory concurrency and all migration tests
  passed against Testcontainers PostgreSQL.
- Spotless, Checkstyle, SpotBugs, JaCoCo report generation, and packaging: passed.
- OpenAPI, AsyncAPI, and JSON event schema validation: passed. Redocly reports 23 non-blocking
  local-server/shared-component/operation-response warnings and no errors.
- Compose model, Docker image builds, service and database health, Kafka topics, OpenSearch,
  Prometheus targets, Tempo search, and structured correlation logs: passed.
- Diff whitespace, secret-pattern, bounded-context database, and generated-file reviews: passed.

## Git Information

- Base: `origin/main`
- Branch: `milestone/02-catalog-inventory`
- Push target: `origin/milestone/02-catalog-inventory`
- Commit style: logical Conventional Commits
- Pull request target: `main`; the pull request must not be merged as part of this task.

## Known Limitations

- Milestone 2 remains API-only; it has no storefront, seller portal, or administrator UI.
- Reservations are a protected foundation for the future checkout workflow; carts and checkout
  orchestration are intentionally absent.
- Image records contain metadata and processing state only. Upload orchestration and media
  processing are outside this milestone.
- Search is eventually consistent with Catalog and Seller state. Public direct product detail uses
  Catalog as the source of truth.
- Local Compose credentials and internal service keys are explicit development placeholders;
  production workload identity and secret management remain deployment work.

## Exit Criteria

- [x] Milestone 1 was merged into and synchronized from `origin/main` before work began.
- [x] Only Catalog, Inventory, and required Search projection scope was implemented.
- [x] Bounded contexts own their databases and do not share persistence entities.
- [x] Seller ownership, publication, price, inventory, concurrency, and failure tests pass.
- [x] Event consumers are idempotent and published contracts remain compatible.
- [x] OpenAPI, AsyncAPI, event schemas, migrations, and index rebuild capability are present.
- [x] The complete Compose environment builds, starts, and passes smoke checks.
- [x] Documentation, ADRs, runbook, threat model, metrics, tracing, and health checks are updated.
- [x] No secret, generated build output, or unrelated later-milestone feature is included.
- [x] The branch is ready to push and open for review without merging.

## Final Status

MILESTONE COMPLETE - READY FOR REVIEW
