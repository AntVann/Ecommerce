#!/usr/bin/env sh
set -eu

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPOSITORY_ROOT"

wait_http() {
  name=$1
  uri=$2
  header=${3:-}
  attempts=30
  while [ "$attempts" -gt 0 ]; do
    if [ -n "$header" ]; then
      curl --fail --silent --show-error --dump-header /tmp/marketflow-smoke-headers \
        --header "$header" "$uri" > /tmp/marketflow-smoke-response && result=0 || result=$?
    else
      curl --fail --silent --show-error --dump-header /tmp/marketflow-smoke-headers \
        "$uri" > /tmp/marketflow-smoke-response && result=0 || result=$?
    fi
    if [ "$result" -eq 0 ]; then
      echo "PASS $name ($uri)"
      return 0
    fi
    attempts=$((attempts - 1))
    sleep 2
  done
  echo "$name did not become ready: $uri" >&2
  return 1
}

wait_prometheus_target() {
  job=$1
  attempts=30
  query="http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22${job}%22%7D"
  while [ "$attempts" -gt 0 ]; do
    if curl --fail --silent --show-error "$query" > /tmp/marketflow-prometheus-query && \
       grep -q '"value":\[[^]]*,"1"\]' /tmp/marketflow-prometheus-query; then
      echo "PASS Prometheus target $job"
      return 0
    fi
    attempts=$((attempts - 1))
    sleep 2
  done
  echo "Prometheus did not report a healthy $job target." >&2
  return 1
}

SMOKE_CORRELATION_ID=m4-infrastructure-smoke
wait_http "sample readiness" "http://localhost:8080/actuator/health/readiness" \
  "X-Correlation-ID: $SMOKE_CORRELATION_ID"
grep -q '"status":"UP"' /tmp/marketflow-smoke-response
grep -qi "^X-Correlation-ID: $SMOKE_CORRELATION_ID" /tmp/marketflow-smoke-headers
wait_http "identity readiness" "http://localhost:8081/actuator/health/readiness" \
  "X-Correlation-ID: $SMOKE_CORRELATION_ID"
grep -q '"status":"UP"' /tmp/marketflow-smoke-response
wait_http "seller readiness" "http://localhost:8082/actuator/health/readiness" \
  "X-Correlation-ID: $SMOKE_CORRELATION_ID"
grep -q '"status":"UP"' /tmp/marketflow-smoke-response
for service in catalog:8083 inventory:8084 search:8085 cart:8086 order:8087 payment:8088; do
  name=${service%:*}
  port=${service#*:}
  wait_http "$name readiness" "http://localhost:$port/actuator/health/readiness" \
    "X-Correlation-ID: $SMOKE_CORRELATION_ID"
  grep -q '"status":"UP"' /tmp/marketflow-smoke-response
done
wait_http "sample metrics" "http://localhost:8080/actuator/prometheus"
grep -q 'jvm_info' /tmp/marketflow-smoke-response
wait_http "identity metrics" "http://localhost:8081/actuator/prometheus"
grep -q 'authentication_failure_total' /tmp/marketflow-smoke-response
wait_http "seller metrics" "http://localhost:8082/actuator/prometheus"
grep -q 'authorization_denied_total' /tmp/marketflow-smoke-response
wait_http "Prometheus API" "http://localhost:9090/api/v1/targets"
for job in sample-service identity-service seller-service catalog-service inventory-service search-service cart-service order-service payment-service; do
  wait_prometheus_target "$job"
done
wait_http "Grafana" "http://localhost:3000/api/health"
wait_http "Tempo" "http://localhost:3200/ready"
wait_http "OpenSearch" "http://localhost:9200/_cluster/health"
wait_http "SeaweedFS master" "http://localhost:9333/cluster/status"

docker compose exec -T postgres pg_isready -U marketflow_local -d marketflow_foundation
docker compose exec -T identity-postgres pg_isready -U identity_app -d marketflow_identity
docker compose exec -T seller-postgres pg_isready -U seller_app -d marketflow_seller
docker compose exec -T catalog-postgres pg_isready -U catalog_app -d marketflow_catalog
docker compose exec -T inventory-postgres pg_isready -U inventory_app -d marketflow_inventory
docker compose exec -T search-postgres pg_isready -U search_app -d marketflow_search
docker compose exec -T order-postgres pg_isready -U order_app -d marketflow_order
docker compose exec -T payment-postgres pg_isready -U payment_app -d marketflow_payment
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.identity.events.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.seller.events.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.catalog.events.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.inventory.events.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.order.events.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | \
  grep -q marketflow.payment.events.v1
docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping
docker compose exec -T redis redis-cli ping | grep -q PONG

sleep 3
wait_http "Tempo trace search" "http://localhost:3200/api/search?limit=20"
grep -q '"traceID"' /tmp/marketflow-smoke-response
docker compose logs --no-color --tail 300 sample-service identity-service seller-service catalog-service inventory-service search-service cart-service order-service payment-service | \
  grep -q "\"correlationId\":\"$SMOKE_CORRELATION_ID\""
echo "PASS structured correlation log"

echo "All MarketFlow Milestone 4 infrastructure smoke checks passed."
