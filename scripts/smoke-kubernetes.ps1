$ErrorActionPreference = 'Stop'
$namespace = 'marketflow-local'
$services = @(
  @{ Name = 'identity-service'; Port = 8081 },
  @{ Name = 'seller-service'; Port = 8082 },
  @{ Name = 'catalog-service'; Port = 8083 },
  @{ Name = 'inventory-service'; Port = 8084 },
  @{ Name = 'search-service'; Port = 8085 },
  @{ Name = 'cart-service'; Port = 8086 },
  @{ Name = 'order-service'; Port = 8087 },
  @{ Name = 'payment-service'; Port = 8088 },
  @{ Name = 'notification-service'; Port = 8089 }
)

kubectl get namespace $namespace | Out-Null
foreach ($service in $services) {
  kubectl -n $namespace rollout status "deployment/$($service.Name)" --timeout=180s
  kubectl -n $namespace get service $($service.Name) | Out-Null
}
Write-Output "MarketFlow Kubernetes smoke checks passed for $namespace."
