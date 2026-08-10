# API Documentation

## Normative contracts

The OpenAPI files in contracts/openapi are authoritative for synchronous APIs. The AsyncAPI file and schemas under contracts/events and contracts/messages are authoritative for messaging. This directory explains how to read and exercise them; it does not replace the schemas.

Validate contracts with:

`powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-contracts.ps1
`

## Boundary conventions

- Public endpoints use /api/v1.
- Internal service calls use /internal/v1 and must not be internet-routable.
- Protected endpoints require the identity token and service-level role/ownership checks.
- Errors use the repository problem-details shape and stable error codes.
- Checkout requires Idempotency-Key; the request hash prevents reuse with different input.
- Money is represented with an explicit amount and ISO currency.
- Pagination and sorting are constrained by each service contract.

## API areas

| Guide | Contract |
|---|---|
| Identity and sessions | contracts/openapi/identity-service.yaml |
| Seller and administration | contracts/openapi/seller-service.yaml |
| Catalog and image metadata | contracts/openapi/catalog-service.yaml |
| Search | contracts/openapi/search-service.yaml |
| Inventory | contracts/openapi/inventory-service.yaml |
| Cart | contracts/openapi/cart-service.yaml |
| Orders and checkout | contracts/openapi/order-service.yaml |
| Fake payments | contracts/openapi/payment-service.yaml |
| Notifications | contracts/openapi/notification-service.yaml |

The frontend uses these real APIs through frontend/web/src/api/client.ts. It does not implement mock business behavior.
