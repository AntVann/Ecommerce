# Operational Runbooks

| Runbook | Use |
|---|---|
| local-development.md | Start, inspect, and stop the local Compose environment |
| identity-seller-local.md | Identity and seller setup and diagnostics |
| catalog-inventory-search-local.md | Catalog, inventory, and search projection operations |
| cart-checkout-local.md | Cart and checkout troubleshooting |
| payment-order-local.md | Fake payment and order Saga behavior |
| notification-fulfillment-local.md | Notification retries and seller shipment workflow |
| dead-letter.md | Inspect and safely redrive terminal messages |
| outbox-backlog.md | Diagnose unpublished events and relay failures |
| chaos-recovery.md | Run local dependency interruption drills |
| backup-restore.md | Create and validate disposable PostgreSQL restores |
| database-migration.md | Migration sequencing and rollback constraints |
| local-kubernetes-deployment.md | Local cluster deploy, probes, and rollback |
| cloud-rollback.md | Production-style rollback documentation for the local profile |
| secret-rotation.md | Local placeholder secret rotation |

Runbooks are procedures, not availability guarantees. Use disposable local data, never commit generated output, and record evidence for any claimed result.

