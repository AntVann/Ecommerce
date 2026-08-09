# Notification and Fulfillment Local Runbook

## Start

```bash
docker compose up -d
docker compose ps
```

Notification service readiness is available at `http://localhost:8089/actuator/health/readiness`.
RabbitMQ management is available at `http://localhost:15672` using the local Compose credentials.

## Notification scenarios

Set `FAKE_EMAIL_SCENARIO` to `success`, `transient-failure`, `permanent-failure`, or `timeout`.
Transient failures retry with bounded exponential backoff. Jobs and attempts remain in the
notification PostgreSQL database; a terminal job is marked `DEAD_LETTERED` and the Rabbit message
is retained in the notification DLQ when the worker cannot acknowledge it.

## Inspect and redrive

1. Identify the job ID, source event ID, and correlation ID from the notification database and
   RabbitMQ management UI.
2. Confirm the provider/template failure is corrected and the original event is valid.
3. Redrive only through an authorized operator procedure; do not edit the job payload directly.
4. Preserve the original job and attempts, create a new auditable attempt, and verify the job is
   `QUEUED` or `DELIVERED` without duplicating the customer effect.

Poison messages and schema failures must remain in the DLQ until the root cause is fixed. Never
place credentials, access tokens, payment tokens, or full addresses in messages or logs.

## Fulfillment

Seller owners and managers with `FULFILLMENT_WRITE` create shipments only for their own order
lines. Shipment creation requires an `Idempotency-Key`; state transitions use `If-Match` and move
from `CREATED` to `IN_TRANSIT` to `DELIVERED`. Customers view shipments through their own order
endpoint. Carrier integrations are not included; carrier and tracking values are validated local
metadata only.
