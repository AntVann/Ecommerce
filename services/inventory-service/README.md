# Inventory Service

Owns inventory items, immutable stock movements, reservations, and expiry. It listens on port 8084
and owns `marketflow_inventory` on local port 5436.

Database constraints and conditional updates enforce `on_hand >= reserved >= 0`. Reservations
lock lines in deterministic variant order, are idempotent by reference ID, and never implement cart
or checkout orchestration. Seller adjustments require a live `INVENTORY_WRITE` decision.

Catalog events initialize variant inventory. Inventory events are published from the transactional
outbox to `marketflow.inventory.events.v1`.
