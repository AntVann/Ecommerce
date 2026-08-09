# Free local Kubernetes profile

This directory describes a zero-cost, single-node deployment profile for portfolio demonstrations.
It deploys the stateless MarketFlow services and references free/local dependencies by DNS name.
The existing `docker-compose.yml` remains the supported way to run PostgreSQL, Kafka, RabbitMQ,
Redis, OpenSearch, SeaweedFS, and observability locally. A developer may expose those dependencies
to a local cluster, or deploy equivalent non-production containers in the same cluster.

The profile is intentionally not a high-availability or production service. It uses one replica by
default, free/local image tags, and an empty Secret object that must be populated out-of-band.
Never commit credentials, certificates, or private keys.

```powershell
kubectl apply --dry-run=client -k platform/kubernetes
kubectl apply -k platform/kubernetes
```

The Ingress references an externally-created `marketflow-local-tls` Secret. For local testing,
install an ingress controller and create a development-only certificate or use a port-forward.
The disabled migration example is a guardrail; staging migrations must be enabled only with a
reviewed per-service Job and least-privilege database credentials.
