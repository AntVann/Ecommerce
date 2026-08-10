# Observability Architecture

## Signals

- Structured application logs include service, event/action, outcome, and correlation identifiers.
- Prometheus-compatible metrics cover request outcomes, business transitions, queue/outbox state, and health.
- OpenTelemetry traces connect synchronous calls and asynchronous processing when propagation is available.
- Spring Boot health endpoints expose liveness/readiness behavior.

## Topology

`mermaid
flowchart LR
    service[Services] --> logs[Structured logs]
    service --> metrics[Prometheus]
    service --> otel[OpenTelemetry collector]
    otel --> traces[Tempo trace backend]
    metrics --> grafana[Grafana dashboards]
    logs --> operator[Local operator]
    traces --> operator
    grafana --> operator
`

Local dashboards and alert rules are under platform/observability. Use docs/runbooks/local-development.md and docs/runbooks/chaos-recovery.md for checks.

## Signals to inspect

- Checkout failures, Saga duration, manual-review count, and compensation backlog.
- Inventory reservation failures, release failures, and invariant warnings.
- Payment decline/unknown outcomes and duplicate callback handling.
- Kafka consumer lag and outbox oldest-unpublished age.
- RabbitMQ queue age, retry count, and DLQ depth.
- Request latency/error rate and readiness state.

A known local limitation is that some containers attempt OTLP metric export to localhost:4318 while Prometheus metrics remain available; see docs/release/release-candidate-report.md.

