# Data Ownership

## Ownership map

```mermaid
flowchart LR
    identityDB[(identity PostgreSQL)] --- identity[Identity]
    sellerDB[(seller PostgreSQL)] --- seller[Seller]
    catalogDB[(catalog PostgreSQL)] --- catalog[Catalog]
    inventoryDB[(inventory PostgreSQL)] --- inventory[Inventory]
    orderDB[(order PostgreSQL)] --- order[Order]
    paymentDB[(payment PostgreSQL)] --- payment[Payment]
    notificationDB[(notification PostgreSQL)] --- notification[Notification]
    cartRedis[(Redis)] --- cart[Cart]
    searchOS[(OpenSearch)] --- search[Search projection]
```

## Rules

- A database is private to its bounded context.
- UUIDs crossing service boundaries are opaque; cross-service foreign keys are not used.
- Catalog is authoritative for product and price data.
- Inventory is authoritative for availability, reservations, and stock movements.
- Order owns immutable commercial/address snapshots and checkout state.
- Payment owns payment state and attempt history.
- Search can be deleted and rebuilt from catalog/seller events.
- Cart estimates are disposable; checkout revalidates current catalog, seller, inventory, and address state.
- PostgreSQL schema changes use migrations and are validated in integration tests.

These rules are recorded in ADR-003 and ADR-004 and are enforced by service boundaries and contracts.
