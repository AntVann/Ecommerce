# Order Service

Owns authenticated checkout, immutable order snapshots, checkout idempotency, the initial Order
aggregate, and the inventory-reservation Saga. It listens on port 8087 and owns the
`marketflow_order` PostgreSQL database on local port 5438.

Checkout revalidates the live customer account, cart version, Catalog facts and prices, seller
status, and Inventory availability. Cart prices are advisory. All commercial values use
`NUMERIC(19,4)`/`BigDecimal`, and orders are single-currency in Milestone 3.

`POST /api/v1/checkouts` requires a CUSTOMER access token and an `Idempotency-Key`. An identical
retry returns the original order; reusing a key with different input returns `409`. The order,
items, status history, Saga, idempotency result, and `order.order-created.v1` outbox record commit
atomically. `GET /api/v1/orders/{orderId}` enforces customer ownership.

The Order service consumes the existing variant-level Inventory reserved, reservation-failed, and
released events. Inbox deduplication and per-variant progress prevent duplicate transitions. It
does not authorize or capture payment, confirm orders, fulfill shipments, or deliver notifications.

Operational endpoints are `/actuator/health/liveness`, `/actuator/health/readiness`, and
`/actuator/prometheus`. Logs use ECS structured JSON and propagate `X-Correlation-ID`.
