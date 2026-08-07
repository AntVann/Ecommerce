# Cart and Checkout Local Runbook

## Scope

This runbook covers the Milestone 3 Cart service on port 8086, Order service on port 8087, the
Order-owned PostgreSQL database on port 5438, Redis cart storage, and Kafka order/inventory events.
Payment, final confirmation, fulfillment, and notification delivery are not present.

## Start and verify

1. Run `docker compose config --quiet`.
2. Run `docker compose up -d --build --wait`.
3. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-infra.ps1` on
   Windows, or `./scripts/smoke-infra.sh` on POSIX systems.
4. Confirm Cart and Order readiness at `/actuator/health/readiness` on ports 8086 and 8087.
5. Confirm Prometheus reports `cart-service` and `order-service` as healthy targets.

## Diagnose carts

- A Redis readiness failure is fail-closed for cart operations. Check `docker compose ps redis`
  and `docker compose exec -T redis redis-cli ping`.
- Guest carts expire after seven days and customer carts after thirty days by default. Both values
  are configurable ISO-8601 durations; changing them does not retroactively extend existing keys.
- Never paste guest tokens, cookies, authorization headers, or full cart documents into tickets.
  Use the correlation ID and sanitized service logs.

## Diagnose checkout and reservations

- Check Order readiness, then its database with
  `docker compose exec -T order-postgres pg_isready -U order_app -d marketflow_order`.
- A repeated idempotency key with the same payload returns the original order. Reuse with a
  different payload is rejected and should be investigated as a client defect or abuse signal.
- If an order remains pending, inspect Order outbox metrics, Kafka topic health, Inventory consumer
  logs, and Order inbox/Saga metrics using the same correlation ID.
- Inventory owns expiry and release. Do not repair a reservation by updating Order or Inventory
  tables manually. Preserve evidence and use the documented protected operation or replay path.
- `MANUAL_REVIEW` is a safe terminal escalation for an ambiguous integration outcome. Milestone 3
  does not authorize payment or advance the order to confirmation.

## Data and rollback

Cart state is disposable and uses the `marketflow:local:cart:v1` Redis namespace. Order data and
idempotency records are durable. Migrations are additive; never delete the Order volume as a
rollback technique when it contains data that must be retained.
