# Identity and Seller Local Runbook

## Boundaries

Identity listens on port 8081 and owns the `marketflow_identity` database on local port 5433.
Seller listens on port 8082 and owns the `marketflow_seller` database on local port 5434. Neither
application credential has access to the other database.

The OpenAPI source contracts are `contracts/openapi/identity-service.yaml` and
`contracts/openapi/seller-service.yaml`. There is no browser UI in Milestone 1.

## Verification delivery

Registration creates a queued verification request and an outbox event. It does not expose a raw
token in the public response or event. A future Notification worker will authenticate to the
one-time internal token-claim endpoint and deliver the returned token. Repeated claims are denied.

## Initial administrator

No public endpoint can grant the ADMIN role. Tests create an administrator fixture directly in an
isolated database. A production operator provisioning mechanism will be connected to the deployed
secret/workload-identity platform; do not add a bootstrap password or ADMIN self-registration.

For local API exploration only, register and verify a disposable user, then grant ADMIN directly
inside the isolated Identity database using the local database owner. Record the user UUID and do
not reuse local credentials or data outside this Compose project.

## Operational checks

- `docker compose ps` must show both services and both service databases healthy.
- Identity readiness includes PostgreSQL, Redis, and signing-key initialization.
- Seller readiness includes PostgreSQL and its Identity liveness dependency.
- Prometheus exposes `authentication_failure_total`, `login_rate_limited_total`,
  `token_reuse_detected_total`, `authorization_denied_total`, and seller status changes.
- Grafana provisions the `MarketFlow Security` dashboard.
- Kafka must contain `marketflow.identity.events.v1` and `marketflow.seller.events.v1`.

## Failure behavior

- Identity fails login closed when Redis rate limiting is unavailable.
- Seller sensitive operations return a retryable dependency error when live Identity validation is
  unavailable.
- Kafka failures do not roll back committed business transactions; outbox attempts are retried.
- Do not delete service database volumes during diagnosis unless their loss is explicitly accepted.
