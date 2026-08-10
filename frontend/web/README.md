# MarketFlow web

The local-only React storefront, seller workspace, and admin console. It uses real service APIs through Vite's same-origin development proxy; it does not ship mock business behavior or require paid infrastructure.

## Local development

1. Start the repository services with `docker compose up -d`.
2. Run `npm ci` and `npm run dev` in this directory.
3. Open <http://localhost:5173>.

The proxy routes `/identity`, `/seller`, `/catalog`, `/inventory`, `/search`, `/cart`, `/order`, and `/notification` to the corresponding Compose ports. Cookie paths are rewritten for the local proxy so refresh and guest-cart CSRF continue to work. Set `VITE_DEMO_SELLER_ID` for seller inventory/order views.

The backend remains authoritative for authorization, prices, stock, seller state, order state, and payment outcomes. The UI only accepts fake payment tokens such as `mf_fake_approve` and never accepts card numbers.

Some screens intentionally show contract-limitation states: seller product listing, public stock, image uploads, and audit browsing require backend read/upload contracts that are not currently present. These limitations are documented rather than replaced with mock data.
