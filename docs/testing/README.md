# Testing and Evidence

## Test layers

- Unit tests cover domain rules, validation, state transitions, and adapters at boundaries.
- Integration and migration tests use real service infrastructure where persistence or messaging behavior matters.
- Contract checks validate OpenAPI, AsyncAPI, and JSON Schema compatibility.
- Frontend tests cover validation and component behavior; Playwright covers browser-critical paths.
- Concurrency, idempotency, duplicate-event, callback, compensation, retry, and DLQ tests cover failure-prone workflows.
- Local scripts exercise smoke, chaos, backup, restore, and operational behavior.

## Verified commands

Commands used by the project include:

`powershell
.\mvnw.cmd -B clean verify
docker compose config
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-contracts.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-infra.ps1
cd frontend/web
npm ci
npm run lint
npm run test
npm run build
npm run test:e2e
`

Milestone reports and docs/release/release-candidate-report.md record which commands actually passed in a given environment. A command that was unavailable is documented as unavailable. This project does not infer test counts, latency, throughput, uptime, or security severity from code inspection.

