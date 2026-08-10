# Milestone 7 performance report

## Test environment

- Environment: local Docker Compose only; no paid hosted load service.
- Host: Docker Desktop 29.6.2 on Windows; services ran with `docker compose up -d --build --wait`.
- Git commit at execution: the `milestone/07-hardening` branch with the hardening fixes applied.
- Dataset: a disposable published `Demo Laptop` product and a separate one-unit variant were
  seeded in the local Compose databases. The fixture is local-only and is not committed.
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
| Product detail/browse | 192 requests / 10 s / 2 VUs | 4.17 ms | 4.88 ms | not emitted | 0% | Passed |
| Product search | 172 requests / 10 s / 2 VUs | 9.71 ms | 16.47 ms | not emitted | 0% | Passed |
| Cart reads | 188 requests / 10 s / 2 VUs | 5.19 ms | 6.38 ms | not emitted | 0% | Passed |
| Concurrent final-unit checkout | 10 iterations / 10 VUs | 8.34 ms | 9.12 ms | not emitted | 0% | Passed safe acknowledgement checks |

The search, cart, browse, and checkout rows were captured from k6 summaries. The checkout load run
used a shared disposable cart and therefore validates safe acknowledgement/error handling rather
than serving as a production-scale oversell proof. A separate authenticated one-unit fixture check
reserved the only unit, and a second customer checkout was rejected with HTTP 409; inventory
remained `on_hand=1, reserved=1`. These are local demonstration objectives, not production SLOs.

## Fixture-backed authenticated demonstrations

- Approved fake payment: checkout progressed to `INVENTORY_RESERVED` and then `CONFIRMED`.
- Declined fake payment: checkout reached `PAYMENT_FAILED`; the individual inventory reservation
  was released.
- Duplicate fake callback: checkout reached `CONFIRMED` with one persisted payment attempt.
- Low-stock safety: the one-unit variant accepted one reservation and rejected the next customer
  checkout with HTTP 409; no negative stock was observed.

## Query-plan review

Local PostgreSQL `EXPLAIN (COSTS OFF)` was run against the Compose databases:

- Inventory availability used `inventory_item_pkey` for the `variant_id` lookup (index scan).
- Order outbox polling used the existing partial `ix_order_outbox_pending` index for
  `next_attempt_at <= now()`; PostgreSQL sorted the qualifying rows by `occurred_at` before the
  bounded `LIMIT 50`.

No additional index migration was justified by these plans. A larger, representative dataset and
production statistics are still required before JVM or query tuning decisions are made.
