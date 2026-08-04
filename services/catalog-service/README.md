# Catalog Service

Owns products, variants, categories, prices, publication state, and product-image metadata. It
listens on port 8083 and owns `marketflow_catalog` on local port 5435.

Seller mutations require a signed access token and a live `CATALOG_WRITE` decision from Seller.
Publication requires an approved seller, active variant, valid price, description, category, and a
ready image. Image bytes are never stored in the Catalog database.

Authoritative APIs are described by `contracts/openapi/catalog-service.yaml`. Domain events are
written to the outbox in the product transaction and published to
`marketflow.catalog.events.v1`.
