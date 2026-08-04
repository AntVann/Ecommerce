# Catalog, Inventory, and Search Local Runbook

## Service boundaries

Catalog, Inventory, Search, Seller, and Identity have separate application users and databases.
Cross-context UUIDs are opaque; never add cross-database foreign keys or direct database grants.

## Startup and health

Run `docker compose up -d --wait`, then verify ports 8083, 8084, and 8085 at
`/actuator/health/readiness`. Kafka must contain `marketflow.catalog.events.v1` and
`marketflow.inventory.events.v1`. Search readiness includes PostgreSQL and OpenSearch; Catalog and
Inventory readiness includes their own PostgreSQL stores.

## Search rebuild

Invoke `POST /internal/v1/search/rebuild` with the internal service key. The worker creates a new
versioned index, exports active products from Catalog in pages, records failures, and atomically
switches the read alias. Do not delete the old index until document counts and search behavior have
been verified.

## Operational signals

- `catalog_product_created_total`, `catalog_product_published_total`, and
  `catalog_price_changed_total` track catalog changes.
- `inventory_adjustment_total` and `inventory_contention_total` track stock behavior.
- `search_projection_total` and `search_rebuild_total` track projection health.
- Pending outbox age/count and Kafka consumer lag must be alerted before production rollout.

## Failure behavior

- Seller authorization failure blocks seller writes; it never falls back to JWT role alone.
- Kafka failure leaves committed events pending in the outbox for retry.
- OpenSearch failure makes Search unavailable but does not block Catalog product detail or writes.
- A failed multi-line reservation rolls back all line changes and emits a separate failure event.
