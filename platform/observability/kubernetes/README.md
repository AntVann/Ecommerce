# Kubernetes observability

The services expose Prometheus metrics and OpenTelemetry traces using the existing repository
conventions. `marketflow-alerts.yaml` is an optional Prometheus Operator resource for clusters
that provide the `PrometheusRule` CRD. In a minimal local cluster, import the same expressions into
the existing Prometheus configuration instead of installing the operator.

Dashboards should cover deployment health, request latency/errors, outbox age, Kafka lag, RabbitMQ
queue/DLQ state, database pools, Redis, OpenSearch, migration Jobs, and release/rollback events.
