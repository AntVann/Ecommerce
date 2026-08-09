# MarketFlow Helm chart

The chart is a provider-neutral, free/local deployment profile. It renders the stateless service
Deployments, ClusterIP Services, ConfigMap, external Secret reference, probes, resources, HPA,
PDB, NetworkPolicies, Ingress/TLS reference, and an opt-in migration hook.

```powershell
helm lint platform/helm/marketflow
helm template marketflow platform/helm/marketflow -f platform/helm/marketflow/values-free.yaml
helm upgrade --install marketflow platform/helm/marketflow `
  --namespace marketflow-local --create-namespace `
  -f platform/helm/marketflow/values-free.yaml
```

The chart does not create managed cloud services. PostgreSQL, Kafka, RabbitMQ, Redis, OpenSearch,
object storage, and OpenTelemetry endpoints are configured through values and may point to the
local Compose stack or an approved external environment. Secret values and TLS material are
always supplied out-of-band.
