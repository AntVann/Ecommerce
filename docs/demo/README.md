# MarketFlow Demo

## Local setup

Start Compose, wait for health, and run the seeded fixture:

```powershell
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-demo-checkout.ps1
cd frontend/web
npm ci
npm run dev
```

Open http://localhost:5173.

## Demonstration path

1. Use the seeded customer to sign in.
2. Browse the published demo laptop and inspect a variant's live availability.
3. Add it to the cart and complete checkout with a shipping address.
4. Wait for inventory reservation, then choose Approve payment.
5. Show the confirmed order and payment state.
6. Repeat with the fake decline outcome and show compensation without confirmation.
7. Use the seller workspace to inspect the seller-scoped order and shipment workflow when the local seller context is configured.
8. Open Prometheus/Grafana and correlate a request with service logs and traces.

The scripts and prior milestone reports are the evidence source. Do not present simulated payment, fake email, or local single-node deployment as production integrations.
