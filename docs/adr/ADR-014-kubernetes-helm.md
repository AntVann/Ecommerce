# ADR-014: Kubernetes and Helm deployment model
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Production-style deployment needs repeatable configuration, probes, scaling, policy, and rollback.
## Decision
Deploy stateless services to managed Kubernetes using a versioned MarketFlow Helm chart.
## Alternatives considered
Raw manifests duplicate configuration; serverless platforms constrain the target broker and service topology.
## Consequences
Workloads declare resources, probes, service accounts, disruption budgets, and network policies.
## Security implications
Use workload identity and external secret references; never embed secrets in chart values.
## Operational implications
The same immutable image digest is promoted with readiness and smoke gates.
## Migration / rollback
Application rollbacks use Helm history when schemas remain compatible; otherwise use forward recovery.

