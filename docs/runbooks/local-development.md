# Local Development Runbook

## Start

1. Install Java 21, Docker Desktop, and either GNU Make or PowerShell 7.
2. Run `make bootstrap` or
   `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\bootstrap.ps1`.
3. Run `docker compose up -d --wait`.
4. Run the platform smoke script.

## Diagnose startup failures

1. Run `docker compose ps` and identify unhealthy or restarting containers.
2. Run `docker compose logs --tail 200 <service>`; redact credentials before sharing output.
3. Confirm Docker has sufficient memory for Kafka and OpenSearch.
4. Confirm ports documented in the root README are free.
5. Validate rendered configuration with `docker compose config --quiet`.

## Telemetry checks

- Sample readiness: `http://localhost:8080/actuator/health/readiness`
- Identity readiness: `http://localhost:8081/actuator/health/readiness`
- Seller readiness: `http://localhost:8082/actuator/health/readiness`
- Catalog readiness: `http://localhost:8083/actuator/health/readiness`
- Inventory readiness: `http://localhost:8084/actuator/health/readiness`
- Search readiness: `http://localhost:8085/actuator/health/readiness`
- Cart readiness: `http://localhost:8086/actuator/health/readiness`
- Order readiness: `http://localhost:8087/actuator/health/readiness`
- Service metrics: `/actuator/prometheus` on ports 8080 through 8087
- Prometheus targets: `http://localhost:9090/targets`
- Grafana health: `http://localhost:3000/api/health`
- Tempo readiness: `http://localhost:3200/ready`

The smoke command also proves correlation-header propagation, ECS JSON request logs, Prometheus
scraping, Milestone 3 Kafka topics, isolated service databases, and trace ingestion in Tempo. See
`docs/runbooks/cart-checkout-local.md` for cart expiry, checkout idempotency, and Saga diagnosis.

## Stop and recovery

`docker compose down` stops containers without deleting local volumes. Do not use `down -v` when
the environment contains data that must be retained. For a disposable local environment, volume
removal still requires explicit confirmation of the project target.
