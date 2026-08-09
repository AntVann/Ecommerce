#!/usr/bin/env bash
set -euo pipefail
namespace="marketflow-local"
services=(identity-service seller-service catalog-service inventory-service search-service cart-service order-service payment-service notification-service)
kubectl get namespace "$namespace" >/dev/null
for service in "${services[@]}"; do
  kubectl -n "$namespace" rollout status "deployment/$service" --timeout=180s
  kubectl -n "$namespace" get service "$service" >/dev/null
done
echo "MarketFlow Kubernetes smoke checks passed for $namespace."
