# Milestone 0: Foundation Completion Evidence

Completed on 2026-08-03 on branch `milestone/00-foundation`. This record covers only Milestone 0;
no Milestone 1 business capability is included.

## Delivered scope

- Maven parent and wrapper with Java 21 enforcement, formatting, Checkstyle, SpotBugs, Failsafe,
  and JaCoCo conventions.
- Non-business Spring Boot sample service with liveness/readiness, Prometheus metrics, OTLP traces,
  ECS JSON logs, and validated correlation IDs.
- OpenAPI common components and RFC 9457-style problem details, an AsyncAPI 3.0 skeleton, and a
  versioned JSON event-envelope schema with locked validators.
- Pinned Compose services for PostgreSQL, Kafka, RabbitMQ, Redis, OpenSearch, S3-compatible local
  object storage, OpenTelemetry Collector, Tempo, Prometheus, and Grafana.
- Cross-platform bootstrap, contract-validation, and infrastructure-smoke entry points.
- Foundation CI, dependency automation, contribution standards, threat model, runbook, architecture
  overview, and ADR-001 through ADR-020.

## Acceptance evidence

| Exit criterion | Result | Evidence |
|---|---|---|
| Build is green | Passed | `mvnw.cmd -B clean verify`: 3 unit tests and 2 integration tests; formatting, Checkstyle, SpotBugs, and JaCoCo report completed. |
| Contract standards validate | Passed | Redocly, AsyncAPI Parser, and AJV validate the OpenAPI, AsyncAPI, event schema, and example. |
| Local infrastructure starts | Passed | `docker compose up -d --build --wait --wait-timeout 300`; all 11 services reached running/healthy state. |
| Health and telemetry are visible | Passed | Smoke checks prove readiness, JVM metrics, Prometheus target health, Grafana/Tempo readiness, trace search, and ECS JSON logs containing the smoke correlation ID. |
| Security scans have no high/critical findings | Passed | npm audit reports zero vulnerabilities; Trivy vulnerability, secret, and misconfiguration scans report zero findings. |
| Repository hygiene passes | Passed | Compose render, actionlint, POSIX script syntax, and `git diff --check` completed successfully. |

Redocly reports expected `no-unused-components` warnings because the two OpenAPI documents are
shared component libraries; those components become referenced by business APIs in later
milestones.

## Operational boundary

The Compose stack is local-development infrastructure: it is single-node, uses local-only example
credentials, and disables OpenSearch transport security inside its isolated Docker network.
Production deployment hardening and Kubernetes resources remain explicitly deferred to Milestone 6.
