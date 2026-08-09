# Milestone 7 performance report

## Test environment

- Environment: local Docker Compose only; no paid hosted load service.
- Host: Docker Desktop 29.6.2 on Windows; services ran with `docker compose up -d --build --wait`.
- Git commit at execution: `f303d32` plus the uncommitted hardening changes.
- Dataset: no durable published-product fixture was present in the local Compose database; search,
  cart, and unauthenticated checkout safety scenarios were executable.
- k6: Grafana k6 `0.55.0` in a disposable Docker container (the native binary is not installed).

## Scenarios and objectives

| Scenario | Script | Objective |
|---|---|---|
| Product detail/browse | `tests/performance/product-browse.js` | p95 < 300 ms |
| Product search | `tests/performance/search.js` | p95 < 500 ms |
| Cart reads | `tests/performance/cart.js` | p95 < 300 ms |
| Concurrent final-unit checkout | `tests/performance/concurrent-checkout.js` | p95 acknowledgement < 1 s; no oversell |

## Results

| Scenario | Runs | p50 | p95 | p99 | Error rate | Result |
|---|---:|---:|---:|---:|---:|---|
| Product detail/browse | Not run | — | — | — | — | Blocked: no published product fixture |
| Product search | 172 requests / 10 s / 2 VUs | 9.71 ms | 16.47 ms | not emitted | 0% | Passed |
| Cart reads | 188 requests / 10 s / 2 VUs | 5.19 ms | 6.38 ms | not emitted | 0% | Passed |
| Concurrent final-unit checkout | 20 iterations / 2 VUs | 3.24 ms | 33.70 ms | not emitted | 0% | Passed safety/acknowledgement checks |

The search, cart, and checkout rows were captured from k6 summaries. Product detail was not
claimed because the local stack has no published-product fixture. These are local demonstration
objectives, not production SLOs; the checkout run validates safe rejection/idempotency handling,
not a successful authenticated purchase or an oversell proof.

## Query-plan review

Local PostgreSQL `EXPLAIN (COSTS OFF)` was run against the Compose databases:

- Inventory availability used `inventory_item_pkey` for the `variant_id` lookup (index scan).
- Order outbox polling used the existing partial `ix_order_outbox_pending` index for
  `next_attempt_at <= now()`; PostgreSQL sorted the qualifying rows by `occurred_at` before the
  bounded `LIMIT 50`.

No additional index migration was justified by these plans. A larger, representative dataset and
production statistics are still required before JVM or query tuning decisions are made.
