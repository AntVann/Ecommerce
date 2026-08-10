# Milestone 7 local performance tests

These scenarios use k6 against the local Docker Compose stack. k6 is not a runtime dependency of
MarketFlow and no hosted load-testing service is required.

Before running a scenario, start Compose and seed a published product. Set `PRODUCT_ID` to an
active product. Checkout and cart scenarios also require a valid customer access token or a guest
cart cookie/CSRF token as described by each script.

```powershell
docker compose up -d --build --wait
k6 run tests/performance/product-browse.js
k6 run tests/performance/search.js
$env:ACCESS_TOKEN = '<local-test-token>'
k6 run tests/performance/cart.js
k6 run tests/performance/concurrent-checkout.js
```

The thresholds are the initial local objectives from `docs/engineering-plan.md`, not production
SLOs. Capture the JSON summary and environment details in
`docs/performance/milestone-07-report.md`; do not commit raw k6 output or credentials.
