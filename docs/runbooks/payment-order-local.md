# Payment and order completion local runbook

Milestone 4 adds Payment Service on port `8088`, backed by `payment-postgres`, and the
`marketflow.payment.events.v1` Kafka topic. It is a deterministic simulator and must never receive
real card or bank data.

## Start and verify

```powershell
docker compose up -d --build --wait
docker compose ps
Invoke-RestMethod http://localhost:8088/actuator/health/readiness
```

Use a customer access token to create and reserve an order, then call
`POST /api/v1/orders/{orderId}/payment-authorizations` with an `Idempotency-Key` and one of the
documented local fake tokens: `mf_fake_approve`, `mf_fake_decline`, `mf_fake_timeout`,
`mf_fake_delayed_approve`, `mf_fake_delayed_decline`, or `mf_fake_duplicate`.

## Expected terminal behavior

- Approval commits Inventory and ends at `CONFIRMED`.
- Decline or deterministic failure releases Inventory and ends at `PAYMENT_FAILED`.
- Timeout remains unknown while reconciliation uses the original provider key.
- Conflicting terminal evidence or failed compensation ends at `MANUAL_REVIEW`.
- Duplicate requests, events, and callbacks do not repeat business effects.

## Diagnostics

Inspect Payment, Order, and Inventory readiness, Kafka consumer lag, pending outbox counts,
authorization outcome counters, unknown-attempt gauges, compensation counters, and trace IDs.
Never paste fake tokens, internal keys, callback signatures, or callback bodies into logs or issue
reports. Local Compose credentials are development placeholders and are not production secrets.
