# Service Responsibilities

| Service | Bounded context | Owns | Does not own |
|---|---|---|---|
| Identity | Identity and access | Accounts, credentials, verification, sessions, roles, login limits, security events | Seller applications or catalog |
| Seller | Seller governance | Applications, seller state, memberships, permissions, review/audit history | Product persistence or orders |
| Catalog | Product catalog | Categories, products, variants, SKU uniqueness, prices, publication, image metadata | Stock quantities or search truth |
| Search | Discovery projection | OpenSearch documents and rebuild checkpoints | Product authority or inventory |
| Inventory | Stock | Items, movements, reservations, expiry, availability | Catalog descriptions or orders |
| Cart | Shopping cart | Guest/customer Redis carts, quantities, advisory price snapshots, merge | Checkout truth or payment |
| Order | Order and checkout | Idempotency records, immutable snapshots, Saga state, order views, shipments | Inventory or payment databases |
| Payment | Payment simulation | Payment aggregate, attempts, fake outcomes, callbacks, payment events | Real financial transactions |
| Notification | Delivery tasks | Templates, attempts, retries, status, fake email behavior | Order correctness |

## Integration rules

- APIs are used for bounded synchronous validation.
- Events are used for durable integration and projections.
- Service-layer authorization is required even when a gateway is present.
- No service reads another service database or imports another service persistence entity.
- Each service has its own migration history and outbox where it publishes events.

