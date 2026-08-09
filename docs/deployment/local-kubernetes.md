# Local Kubernetes deployment

## Scope

Milestone 6 uses a free/local Kubernetes profile for portfolio demonstration. It does not create
or require paid managed cloud infrastructure. Local Compose remains the reference dependency
stack; Kubernetes resources consume dependency endpoints supplied through ConfigMaps and an
out-of-band Secret.

## Prerequisites

- Docker Desktop Kubernetes, kind, k3d, or another local Kubernetes cluster
- `kubectl`
- Helm 3 for the chart workflow
- Local service images built with the repository Dockerfiles
- An ingress controller if testing the Ingress resource

## Build images

Build one image per service from the repository root. The chart defaults to the `marketflow/*:local`
names. For kind, load the images into the cluster; for another local cluster, use its documented
local registry workflow.

## Render and validate

```powershell
kubectl kustomize platform/kubernetes
kubectl apply --dry-run=client -k platform/kubernetes
helm lint platform/helm/marketflow
helm template marketflow platform/helm/marketflow -f platform/helm/marketflow/values-free.yaml
```

The checked-in Secret contains no values. Create the local-only Secret separately and never copy
it into Git. Create `marketflow-local-tls` separately when using the Ingress.

## Deploy and smoke test

```powershell
kubectl apply -k platform/kubernetes
kubectl -n marketflow-local rollout status deployment/identity-service --timeout=180s
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-kubernetes.ps1
```

The smoke test checks namespace resources, deployment availability, service endpoints, and health
probe paths. Dependency failures should leave services unready rather than causing unsafe traffic.

## Rollback

For Helm releases, use `helm history marketflow` and `helm rollback marketflow <revision>` after
confirming schema compatibility. For Kustomize, restore the previous image tags and apply the
previous manifest set. Database migrations are additive and require forward recovery when a
schema change cannot be safely reversed.

This profile is single-node and not an availability guarantee. Paid managed services remain a
future optional deployment profile and are not part of the portfolio default.
