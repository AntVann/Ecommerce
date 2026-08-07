# MarketFlow Cart Service

The Cart bounded context owns expiring guest and authenticated customer carts in Redis. Prices on carts are estimates only; checkout must revalidate current Catalog prices, Seller state, and Inventory availability.

## API

- `GET /api/v1/cart` creates or returns the current cart and emits an ETag.
- `POST /api/v1/cart/items` validates a variant through Catalog and adds it.
- `PATCH /api/v1/cart/items/{variantId}` updates quantity using `If-Match`.
- `DELETE /api/v1/cart/items/{variantId}` and `DELETE /api/v1/cart` use `If-Match`.
- `POST /api/v1/cart/merge` atomically merges the guest cart into the authenticated customer cart.
- `POST /internal/v1/carts/checkout-snapshots` returns an exact customer-owned cart/version to Order.

Quantities are 1–99 and a cart has at most 100 distinct variants by default. Successful mutations refresh the guest seven-day or customer thirty-day TTL. Reads do not refresh TTL.

## Security

Guest identity is a 256-bit random HttpOnly, SameSite=Lax `MARKETFLOW_GUEST_CART` cookie. Only its SHA-256 digest appears in Redis keys. Anonymous unsafe requests use a double-submit `MARKETFLOW_GUEST_CSRF` cookie and `X-CSRF-Token` header. Authenticated identity comes only from the validated JWT subject. Merge and checkout snapshot operations verify the customer remains active through Identity. Internal calls use `X-Internal-Service-Key` and constant-time comparison.

Never log guest cookies, CSRF values, JWTs, or internal keys.

## Local verification

```powershell
mvn -f services/cart-service/pom.xml clean verify
```

Redis integration tests use Testcontainers and skip only when Docker is unavailable.
