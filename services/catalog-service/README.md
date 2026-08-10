# Catalog Service

Owns products, variants, categories, prices, publication state, and product-image metadata. It
listens on port 8083 and owns `marketflow_catalog` on local port 5435.

Seller mutations require a signed access token and a live `CATALOG_WRITE` decision from Seller.
Publication requires an approved seller, active variant, valid price, description, category, and a
ready image. Image bytes are never stored in the Catalog database. For the $0 local profile, the
upload endpoint stores validated JPEG/PNG bytes under `MARKETFLOW_IMAGE_STORAGE_DIR` (defaulting
to the JVM temp directory) and returns metadata; production object storage is intentionally out of
scope.

`GET /api/v1/sellers/{sellerId}/products` is seller-scoped and returns the caller's products.
Public variant availability is served by Inventory at
`GET /api/v1/variants/{variantId}/availability`.

Authoritative APIs are described by `contracts/openapi/catalog-service.yaml`. Domain events are
written to the outbox in the product transaction and published to
`marketflow.catalog.events.v1`.
