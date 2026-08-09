# Milestone 6 Completion Report

## Milestone

Milestone 6: Local Deployment

## Scope delivered

- Standardized multi-stage Java 25 runtime images with non-root users and bounded JVM settings.
- Added free/local Kubernetes manifests under `platform/kubernetes`.
- Added a provider-neutral Helm chart under `platform/helm/marketflow`.
- Added Deployments, ClusterIP Services, ConfigMap, empty Secret reference, ServiceAccount,
  probes, resources, HPA, PDB, NetworkPolicies, Ingress, and TLS Secret references.
- Added opt-in migration guardrails and expand/contract migration documentation.
- Added immutable image build/publish workflow with provenance and SBOM metadata.
- Added manually-triggered staging deployment workflow using an out-of-band kubeconfig Secret,
  Helm wait gates, smoke checks, and rollback behavior.
- Added Kubernetes smoke scripts, dashboards/alert foundations, ADR-026, deployment documentation,
  migration, rollback, and secret-rotation runbooks.

## Free-infrastructure boundary

This milestone does not provision or require paid managed cloud infrastructure. PostgreSQL, Kafka,
RabbitMQ, Redis, OpenSearch, SeaweedFS, and telemetry remain local Compose dependencies or may be
provided by equivalent free/local containers. Managed endpoints are configuration-only options.

The local profile is single-node and does not claim production availability, disaster recovery,
compliance certification, or production SLOs.

## Validation evidence

The following checks were run for this branch:

- `./mvnw -B clean verify` — passed; the full Maven reactor, integration tests, Checkstyle,
  SpotBugs, Spotless, and JaCoCo completed successfully.
- `docker compose config --quiet` — passed.
- `docker compose build --parallel` — passed for all ten application images.
- `docker compose ps` and `scripts/smoke-infra.ps1` (PowerShell execution-policy bypass) — passed;
  all application readiness endpoints and local telemetry/dependency checks passed.
- `kubectl kustomize platform/kubernetes` — passed.
- Kustomize rendered output validated with Kubeconform — 37 resources valid.
- Helm lint and template were run in disposable `alpine/helm:3.17.3` because Helm is not installed
  natively; lint passed and rendered output validated with Kubeconform — 34 resources valid.
- Gitleaks repository scan — passed with no leaks found; no credentials, certificates, or generated
  build output are committed.
- `kubectl apply --dry-run=client -k platform/kubernetes` could not run because the configured
  Docker Desktop Kubernetes API is unavailable; no cluster mutation was attempted.

## Operational limitations

The Docker Desktop Kubernetes API in the development environment must be available for live apply,
rollout, and smoke validation. Client-side rendering and Helm validation remain safe offline checks.

## Exit status

Milestone 6 is complete for the approved free/local deployment scope. Live Kubernetes apply and
rollout checks remain an environment-dependent follow-up when a working local cluster is enabled;
the manifests, chart, offline schema checks, Compose smoke checks, and operational procedures are
validated in this branch.
