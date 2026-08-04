# ADR-015: OpenTelemetry observability standard
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Distributed HTTP, messaging, persistence, and provider workflows require vendor-neutral correlation.
## Decision
Instrument services with OpenTelemetry, Micrometer, Prometheus metrics, and structured JSON logs.
## Alternatives considered
Vendor-specific agents reduce portability; logs alone cannot explain cross-service latency and causation.
## Consequences
Correlation and trace context propagate over HTTP and messaging with safe semantic attributes.
## Security implications
Tokens, credentials, payment data, full addresses, and unbounded payloads are prohibited in telemetry.
## Operational implications
Collectors decouple exporters; dashboards and alerts cover technical and business outcomes.
## Migration / rollback
Exporters can change behind the collector while application instrumentation remains stable.

