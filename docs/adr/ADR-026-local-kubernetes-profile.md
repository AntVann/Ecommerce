# ADR-026: Free local Kubernetes deployment profile

Status: Accepted for Milestone 6
Date: 2026-08-10
Owners: MarketFlow Architecture

## Context

MarketFlow is a portfolio project and must demonstrate deployment, security, probes, observability,
and rollback without requiring a paid cloud account.

## Decision

Provide a provider-neutral Helm chart and Kustomize manifests for a single-node/local Kubernetes
profile. Services run as non-root stateless Deployments. PostgreSQL, Kafka, RabbitMQ, Redis,
OpenSearch, object storage, and telemetry dependencies are supplied by the existing free Compose
stack or equivalent local containers. Managed-cloud endpoints remain external configuration only.

## Consequences

The profile demonstrates release mechanics but does not claim high availability, disaster recovery,
or production SLOs. Image tags, endpoints, credentials, and TLS material are supplied out-of-band.

## Security and operations

NetworkPolicies, probes, resource limits, HPA/PDB resources, secret references, non-root runtime
containers, and rollback runbooks are included. Empty Secret templates never contain credentials.

## Migration / rollback

Local disposable environments may use Flyway startup migration. Staging-like rehearsals use reviewed
service-owned migration Jobs and forward recovery for irreversible schema changes.
