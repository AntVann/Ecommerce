# Checkout Saga

Order is the Saga orchestrator because it owns the customer-visible order state and immutable order snapshot.

## Flow

`mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> INVENTORY_RESERVED: reservation confirmed
    PENDING --> INVENTORY_FAILED: reservation failed
    INVENTORY_RESERVED --> PAYMENT_PROCESSING: authorization requested
    PAYMENT_PROCESSING --> CONFIRMED: payment authorized
    PAYMENT_PROCESSING --> PAYMENT_FAILED: payment declined or failed
    PAYMENT_PROCESSING --> MANUAL_REVIEW: ambiguous timeout/inconsistency
    PAYMENT_FAILED --> INVENTORY_RELEASE_PENDING: compensate
    INVENTORY_RELEASE_PENDING --> CANCELLED: release confirmed
    MANUAL_REVIEW --> CONFIRMED: reviewed outcome
    MANUAL_REVIEW --> CANCELLED: reviewed compensation
`

## Invariants

- Checkout requires an Idempotency-Key and request-body hash.
- Reusing a key with the same request returns the original order; a different request is rejected.
- Prices, seller status, availability, and addresses are revalidated during checkout.
- The order stores immutable item, price, currency, and address snapshots.
- Inventory reservation is atomic and cannot make available stock negative.
- Payment accepts only opaque fake tokens and tracks attempts by idempotency key.
- Payment decline releases inventory; ambiguous outcomes can enter manual review.
- Notification failure does not invalidate a confirmed order.

## Operational evidence

Run the local order/payment workflow with docs/runbooks/payment-order-local.md and inspect outbox/inbox state with docs/runbooks/outbox-backlog.md.

