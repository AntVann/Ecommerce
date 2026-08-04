# MarketFlow Seller Service

Owns seller applications, review decisions, suspension state, status history, seller memberships,
roles, permissions, security events, and Seller outbox records. It treats Identity user IDs as
opaque values and never reads Identity persistence.

Seller ownership and permissions are evaluated from Seller-owned data for every scoped operation.
