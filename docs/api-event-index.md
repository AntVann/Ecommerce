# MarketFlow API and event index

The normative synchronous contracts are the OpenAPI files under `contracts/openapi/`. The
normative asynchronous contracts are `contracts/asyncapi/marketflow.yaml` and the versioned JSON
schemas under `contracts/events/` and `contracts/messages/`.

The public service boundaries are Identity, Seller, Catalog, Search, Inventory, Cart, Order,
Payment, and Notification. Internal endpoints use `/internal/v1` and must not be internet-routable.
Events use the shared envelope, immutable event IDs, aggregate versions, correlation IDs, and
consumer inbox deduplication. Additive fields are compatible; renamed, removed, or semantically
changed fields require a new version and compatibility review.

Validate the index with `powershell -NoProfile -ExecutionPolicy Bypass -File
scripts/validate-contracts.ps1` before release.
