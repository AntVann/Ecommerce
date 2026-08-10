# Milestone 8: Full-Stack UI & Integration Completion

## Scope delivered

- Local-only React/TypeScript/Vite application in `frontend/web`.
- Customer registration, login/logout, catalog browsing/search, product details, guest and
  authenticated carts, cart merge, checkout, simulated payment, order history/details, and
  shipment display.
- Seller onboarding, role-aware seller workspace, inventory and seller-order views.
- Admin seller application review, approval, rejection, suspension, and security-audit browsing.
- TanStack Query server-state management, typed service adapters, CSRF/cookie credentials,
  in-memory access tokens, refresh retry, ETags, idempotency keys, problem-details handling,
  loading/empty/error/permission states, and accessible responsive components.
- Local Vite same-origin proxy for the eight public service ports. No paid APIs, hosted services,
  real payment credentials, card data, or production secrets are included.

## Additive integration surfaces

This milestone adds real backend contracts for seller product listing, public variant
availability, local image upload/delivery, and administrator audit-event browsing. Image bytes are
stored by the catalog service's local filesystem adapter; the database stores only metadata and
object keys. Search filtering remains limited to the available query/category parameters.

## Validation evidence

Executed in `frontend/web`:

- `npm ci`
- `npm run lint`
- `npm run typecheck`
- `npm run test` (2 validation tests passed)
- `npm run build`
- `npm run test:e2e -- checkout.spec.ts` (seeded authenticated customer checkout and simulated
  approval passed against the local Compose services)
- `npm audit --omit=dev --audit-level=moderate` (0 production dependency vulnerabilities after
  upgrading React Router)

The full development dependency tree still reports existing Vite/Vitest/Playwright toolchain
advisories. They are not shipped runtime dependencies and were not force-upgraded as part of this
scoped runtime hardening change.

Executed at repository root:

- `docker compose config` passed.
- Compose services were healthy during the frontend proxy smoke check.
- `/catalog/api/v1/categories` returned real data through the Vite proxy.

The full Maven verification remains an existing backend gate and is rerun before push.

## Repeatable local checkout fixture

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-demo-checkout.ps1` from the
repository root before the checkout E2E. The script creates only disposable local data: an approved
demo seller, an active published laptop product, two variants, 100 units of local inventory, and a
verified customer account. It rebuilds the local OpenSearch projection and prints the product URL
and demo credentials. The identifiers and password are intentionally test-only placeholders and
must not be reused outside the local Compose environment.
