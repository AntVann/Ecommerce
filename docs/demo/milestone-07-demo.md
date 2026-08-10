# Milestone 7 final demonstration script

This script is API- and operations-driven because the repository does not contain a frontend.
Use only local Compose services and fake providers.

## Success

1. Start Compose and run the infrastructure smoke checks.
2. Register/verify a customer and approve a seller.
3. Create a product with two variants, price, image metadata, and stock.
4. Publish the product and confirm Search projection visibility.
5. Add to cart and submit checkout with an idempotency key.
6. Show inventory reservation, fake payment authorization, order confirmation, and notification.
7. Show one trace, Prometheus metrics, structured correlation logs, outbox/inbox records, and
   status history.

## Failure and duplicate paths

1. Configure fake payment decline or timeout and repeat checkout.
2. Show reservation release, `PAYMENT_FAILED` or `UNKNOWN`, and no duplicate authorization.
3. Replay a payment, inventory, or notification event and show one business effect.
4. Stop Kafka, Redis, RabbitMQ, or a database with `scripts/chaos-local.ps1`, restore it, and
   show readiness and backlog recovery.
5. Run the PostgreSQL backup and disposable restore validation.

Record command output and links to the performance, security, recovery, and release-readiness
reports. Do not record tokens, passwords, fake payment tokens, or customer addresses.
