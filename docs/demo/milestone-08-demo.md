# Milestone 8 Local UI Demonstration

1. Run `docker compose up -d --wait` from the repository root.
2. Seed the repeatable local fixture with
   `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-demo-checkout.ps1`.
3. Run `cd frontend/web; npm ci; npm run dev`.
4. Open <http://localhost:5173/products/33333333-3333-3333-3333-333333333333> and log in with the
   credentials printed by the fixture script.
5. Add the published laptop variant to the cart.
6. Complete checkout with the server's fake payment token outcomes (`mf_fake_approve` or
   `mf_fake_decline`) and observe the order state.
7. Review order history, order details, and shipment information.
8. For seller views, set `VITE_DEMO_SELLER_ID` and sign in with an authorized seller principal.
9. For admin views, sign in with an authorized admin principal and review seller applications.

The UI never asks for card numbers. All authorization and state transitions are enforced by the
backend. The fixture is local-only and does not require paid services.
