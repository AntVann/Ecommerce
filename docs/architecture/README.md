# MarketFlow Architecture

The system architecture is defined by `docs/engineering-plan.md` and the accepted ADRs in
`docs/adr`. Public contracts under `contracts/` take precedence over prose when implementation
begins.

## Non-negotiable boundaries

- Each deployable business service owns one bounded context, its invariants, and its persistence.
- Services do not query another service database or share persistence entities.
- API code depends on application code; application code depends on domain code; infrastructure
  implements ports owned by the inner layers.
- Synchronous calls are explicit and bounded. Cross-service state changes use versioned events and
  compensating workflows rather than distributed transactions.
- Retry-prone commands and every event consumer are idempotent.
- Logs, metrics, traces, error codes, and failure behavior are designed with each capability.

The Milestone 0 sample service is intentionally not a bounded context. It proves platform
conventions and must not accumulate business behavior.

## Milestone 1 bounded contexts

- Identity owns accounts, credentials, verification challenges, global roles, access-token keys,
  refresh-token families, revocations, login rate limits, security events, and its outbox.
- Seller owns applications, seller status, review history, memberships, seller roles and
  permissions, security events, and its outbox.
- The two services use separate databases. User and seller UUIDs crossing a boundary are opaque;
  there are no cross-service foreign keys.
- Seller verifies JWT signatures using Identity JWKS and performs a live Identity token-state check
  before sensitive operations. Seller ownership remains a local service-layer and repository
  decision, as recorded by ADR-021.
- Registration and seller-decision events are durable integration boundaries. Their notification,
  catalog, search, and order consumers are deliberately not part of Milestone 1.

## Milestone 2 bounded contexts

- Catalog owns categories, products, variants, seller-scoped SKUs, prices, publication state,
  image metadata, seller-state projections, and its transactional outbox.
- Inventory owns on-hand and reserved quantities, immutable movements, reservations, expiry,
  idempotency records, and its transactional outbox.
- Search owns only OpenSearch documents and projection/rebuild checkpoints. It is never used as
  the authoritative source for product detail or inventory.
- Catalog and Inventory call Seller's protected authorization API; neither can access Seller's
  database. Seller suspension is also propagated through version-aware, idempotent projections.
- Product publication initializes Inventory items through Catalog events. Search projects Catalog
  and Seller events and can rebuild from a protected, paginated Catalog export.

## Milestone 3 bounded contexts

- Cart owns versioned Redis documents for guest and authenticated carts, item quantity rules,
  advisory price snapshots, actor-specific expiry, and deterministic guest-to-customer merging.
- Order owns checkout idempotency records, immutable commercial and address snapshots, the initial
  order state machine, Saga progress, its transactional outbox, and its Inventory-event inbox.
- Checkout reads only protected service contracts: Cart supplies the selected snapshot, Catalog
  supplies current sellable variants and prices, Seller supplies current approval state, and
  Inventory supplies availability. No service reads another context's store.
- Inventory remains the reservation authority. It handles order-created commands idempotently and
  emits the existing reservation success/failure contracts. Payment, confirmation, fulfillment,
  and notification are outside the Milestone 3 state machine.
