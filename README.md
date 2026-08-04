# MarketFlow

MarketFlow is a multi-vendor e-commerce platform implemented as independently owned bounded
contexts. The authoritative product and engineering specification is
[`docs/engineering-plan.md`](docs/engineering-plan.md).

## Milestone 2 scope

The current milestone adds Catalog, Inventory, and Search to the Identity and Seller foundation.
It provides seller-owned products and variants, controlled categories, decimal prices, publication
validation, media metadata, concurrency-safe stock, reservation foundations, transactional domain
events, and a rebuildable OpenSearch product projection.

This remains an API-first backend. It does not contain a storefront, seller portal, or admin UI.
Carts, checkout, payments, order completion, notification delivery, and fulfillment remain future
milestones.

Foundation evidence is recorded in [`docs/milestones/m0-completion.md`](docs/milestones/m0-completion.md).
Milestone 1 evidence is recorded in
[`docs/milestones/milestone-01-completion.md`](docs/milestones/milestone-01-completion.md).
Milestone 2 evidence is recorded in
[`docs/milestones/milestone-02-completion.md`](docs/milestones/milestone-02-completion.md).

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
```

The Compose environment exposes these local-only endpoints:

| Component | URL |
|---|---|
| Sample service | http://localhost:8080/actuator/health/readiness |
| Identity service | http://localhost:8081/actuator/health/readiness |
| Seller service | http://localhost:8082/actuator/health/readiness |
| Catalog service | http://localhost:8083/actuator/health/readiness |
| Inventory service | http://localhost:8084/actuator/health/readiness |
| Search service | http://localhost:8085/actuator/health/readiness |
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
