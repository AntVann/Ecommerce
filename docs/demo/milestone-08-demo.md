# Milestone 8 Local UI Demonstration

1. Run `docker compose up -d --wait` from the repository root.
2. Run `cd frontend/web; npm ci; npm run dev`.
3. Open <http://localhost:5173> and browse products.
4. Register a local customer, complete the repository's email-verification fixture workflow, and
   log in.
5. Add a published variant to the guest cart, log in, and confirm the cart merge.
6. Complete checkout with the server's fake payment token outcomes (`mf_fake_approve` or
   `mf_fake_decline`) and observe the order state.
7. Review order history, order details, and shipment information.
8. For seller views, set `VITE_DEMO_SELLER_ID` and sign in with an authorized seller principal.
9. For admin views, sign in with an authorized admin principal and review seller applications.

The UI never asks for card numbers. All authorization and state transitions are enforced by the
backend. See `frontend/web/README.md` for proxy and contract limitations.
