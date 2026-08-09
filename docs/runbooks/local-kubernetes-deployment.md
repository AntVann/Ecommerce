# Local Kubernetes deployment runbook

## Start

1. Start the local dependency stack with `docker compose up -d --wait`, or provide equivalent
   dependency endpoints in the Helm values.
2. Build and load the service images into the local cluster.
3. Create the out-of-band runtime Secret and optional TLS Secret.
4. Render and validate the Kustomize or Helm resources.
5. Apply the resources and wait for deployment readiness.
6. Run `scripts/smoke-kubernetes.ps1` or `scripts/smoke-kubernetes.sh`.

## Diagnose

- `kubectl -n marketflow-local get pods` identifies image, probe, and scheduling failures.
- `kubectl -n marketflow-local describe pod <pod>` shows failed probes without exposing Secret data.
- `kubectl -n marketflow-local logs deployment/<service> --tail=200` should be sanitized before
  sharing.
- Check the Compose dependency health and the service readiness endpoint before changing images.

## Migration safety

Flyway startup migrations are acceptable for local disposable environments. Staging-like runs must
use one reviewed Job per service, a least-privilege migration identity, and an additive
expand-and-contract sequence. Do not delete database volumes as rollback.

## Rollback

Stop promotion when readiness or smoke checks fail. Helm releases use `helm rollback`; direct
manifest deployments restore the prior image digest and manifest revision. Never force-push or
automatically delete a namespace, database, or volume.
