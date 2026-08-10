# Milestone 8: Full-Stack UI & Integration Completion

## Scope delivered

- Local-only React/TypeScript/Vite application in `frontend/web`.
- Customer registration, login/logout, catalog browsing/search, product details, guest and
  authenticated carts, cart merge, checkout, simulated payment, order history/details, and
  shipment display.
- Seller onboarding, role-aware seller workspace, inventory and seller-order views.
- Admin seller application review, approval, rejection, suspension, and explicit audit-contract
  limitation state.
- TanStack Query server-state management, typed service adapters, CSRF/cookie credentials,
  in-memory access tokens, refresh retry, ETags, idempotency keys, problem-details handling,
  loading/empty/error/permission states, and accessible responsive components.
- Local Vite same-origin proxy for the eight public service ports. No paid APIs, hosted services,
  real payment credentials, card data, or production secrets are included.

## Contract limitations retained intentionally

The backend does not currently expose seller product listing, public inventory availability,
image upload/delivery, or audit-event browsing contracts. The UI presents safe explanatory states
for those areas and does not invent mock production behavior. Search filtering is limited to the
available query/category parameters until an additive search contract is approved.

## Validation evidence

Executed in `frontend/web`:

- `npm ci`
- `npm run lint`
- `npm run typecheck`
- `npm run test` (2 validation tests passed)
- `npm run build`
- `npm run test:e2e` (Playwright storefront navigation smoke passed)
- `npm audit --omit=dev --audit-level=high` (no high/critical runtime findings; two moderate
  React Router advisories remain in the supported Node 16-compatible major)

Executed at repository root:

- `docker compose config` passed.
- Compose services were healthy during the frontend proxy smoke check.
- `/catalog/api/v1/categories` returned real data through the Vite proxy.

The full Maven verification remains an existing backend gate and is rerun before push.
