# MarketFlow

MarketFlow is a multi-vendor e-commerce platform implemented as independently owned bounded
contexts. The authoritative product and engineering specification is
[`docs/engineering-plan.md`](docs/engineering-plan.md).

## Milestone 4 scope

The current milestone adds a fake-token Payment bounded context and completes the initial Order
Saga. It provides idempotent simulated authorization, approval, decline, timeout, delayed and
duplicate callback behavior, Inventory confirmation or release compensation, customer order
history, seller-filtered order views, and manual-review escalation for ambiguous inconsistencies.

Milestone 8 adds a local-only React storefront, seller workspace, and admin console under
[`frontend/web`](frontend/web). The UI uses the real service APIs through a same-origin Vite
proxy; it never replaces authorization, price, inventory, seller, order, or payment decisions.
It never accepts real payment credentials and makes no payment-compliance certification claim.

Milestone 6 adds a free/local Kubernetes and Helm deployment profile. It does not provision paid
managed cloud infrastructure; dependency endpoints and secrets are supplied out-of-band for a
local or free-tier demonstration.

Foundation evidence is recorded in [`docs/milestones/m0-completion.md`](docs/milestones/m0-completion.md).
Milestone 1 evidence is recorded in
[`docs/milestones/milestone-01-completion.md`](docs/milestones/milestone-01-completion.md).
Milestone 2 evidence is recorded in
[`docs/milestones/milestone-02-completion.md`](docs/milestones/milestone-02-completion.md).
Milestone 3 evidence is recorded in
[`docs/milestones/milestone-03-completion.md`](docs/milestones/milestone-03-completion.md).
Milestone 4 evidence is recorded in
[`docs/milestones/milestone-04-completion.md`](docs/milestones/milestone-04-completion.md).
Milestone 8 evidence is recorded in
[`docs/milestones/milestone-08-completion.md`](docs/milestones/milestone-08-completion.md).

## Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- GNU Make on Linux/macOS, or PowerShell 7 on Windows

The Maven wrapper is included; a system Maven installation is not required.

## Quick start

### PowerShell

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\bootstrap.ps1
docker compose up -d --wait
.\mvnw.cmd -B clean verify
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-contracts.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-infra.ps1
```

### POSIX shell

```bash
make bootstrap
make infra-up
make verify
make smoke

# in another terminal, after Compose is healthy
cd frontend/web
npm ci
npm run dev
```

Open <http://localhost:5173> for the local UI. The frontend proxy routes browser requests to the
Compose services. Set
`VITE_DEMO_SELLER_ID` for seller inventory and order views. To seed a repeatable published product
and verified customer for the real checkout flow, run
`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\seed-demo-checkout.ps1` before opening
the UI. The fixture is local-only and uses no mock API behavior or paid infrastructure.

The Compose environment exposes these local-only endpoints:

| Component | URL |
|---|---|
| Sample service | http://localhost:8080/actuator/health/readiness |
| Identity service | http://localhost:8081/actuator/health/readiness |
| Seller service | http://localhost:8082/actuator/health/readiness |
| Catalog service | http://localhost:8083/actuator/health/readiness |
| Inventory service | http://localhost:8084/actuator/health/readiness |
| Search service | http://localhost:8085/actuator/health/readiness |
| Cart service | http://localhost:8086/actuator/health/readiness |
| Order service | http://localhost:8087/actuator/health/readiness |
| Payment service | http://localhost:8088/actuator/health/readiness |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| RabbitMQ management | http://localhost:15672 |
| OpenSearch | http://localhost:9200 |
| S3-compatible object storage | http://localhost:8333 |
| SeaweedFS local admin | http://localhost:23646 |

The bootstrap command creates an ignored `.env` from `.env.example` when needed. Values in the
example are intentionally non-production placeholders. Never reuse them outside an isolated local
environment.

## Repository layout

```text
services/       independently deployable bounded contexts
contracts/      synchronous and asynchronous source contracts
platform/       local and deployment infrastructure
docs/           architecture, ADRs, threat models, and runbooks
scripts/        cross-platform development and validation entry points
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for branch, commit, quality, and security expectations.
Operational details for this milestone are in
[`docs/runbooks/payment-order-local.md`](docs/runbooks/payment-order-local.md).
