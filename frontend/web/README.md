# MarketFlow web

The local-only React storefront, seller workspace, and admin console. It uses real service APIs through Vite's same-origin development proxy; it does not ship mock business behavior or require paid infrastructure.

## Local development

1. Start the repository services with `docker compose up -d`.
2. Run `npm ci` and `npm run dev` in this directory.
3. Open <http://localhost:5173>.

The proxy routes `/identity`, `/seller`, `/catalog`, `/inventory`, `/search`, `/cart`, `/order`, and `/notification` to the corresponding Compose ports. Set `VITE_DEMO_SELLER_ID` for seller inventory/order views.

The backend remains authoritative for authorization, prices, stock, seller state, order state, and payment outcomes. The UI only accepts fake payment tokens such as `mf_fake_approve` and never accepts card numbers.

To exercise the real customer checkout path, run `powershell -NoProfile -ExecutionPolicy Bypass -File
..\..\scripts\seed-demo-checkout.ps1` from the repository root. The fixture seeds a published demo
laptop, local inventory, a verified customer, and an OpenSearch projection; it does not create mock
API responses or require paid infrastructure. Run `npm run test:e2e -- checkout.spec.ts` after the
fixture to verify checkout and simulated approval.
