# MarketFlow Release Candidate Report

## Scope and recommendation

This report records the Milestone 9 release-candidate validation performed against the local,
free Docker Compose environment. The candidate is **conditionally ready for local demonstration**.
The complete seeded customer checkout path and the approved payment-decline compensation path pass,
along with the service, migration, chaos, and backup/restore checks listed below. This is not a
production-readiness or hosted-cloud certification.

## Tested flows

- Infrastructure smoke checks and Compose health/readiness checks.
- Seeded published product (`Aurora Pro Laptop`) and repeatable inventory fixture.
- Customer sign-in, product browse/detail, cart add, checkout, inventory reservation, simulated
  payment approval, order confirmation, and order detail.
- Simulated payment decline with `PAYMENT_FAILED` and compensation; the order does not confirm.
- Frontend smoke, authenticated checkout, and payment-decline Playwright tests: **3 passed**.
- Maven multi-module verification, migrations, integration tests, Checkstyle, and SpotBugs: **passed**.
- Redis, Kafka/order, RabbitMQ/notification, PostgreSQL/identity, and notification-process chaos
  recovery: **passed locally**.
- Nine local PostgreSQL backup dumps restored into disposable PostgreSQL 17 containers: **passed**.

## Failures found and defects fixed

1. The UI exposed payment controls while an order was still `PENDING`; a customer could race
   inventory reservation. Payment controls are now gated on `INVENTORY_RESERVED` or
   `PAYMENT_PROCESSING`, with an explicit waiting state.
2. Adding a product did not invalidate the cached cart query, so navigation could display an empty
   cart. Product detail now invalidates the cart query after a successful add, and the E2E flow
   waits for the authoritative cart response.
3. The demo inventory seed reset counters without removing matching reservation rows. The seed now
   removes fixture reservations first, making repeated demos and release checks deterministic.
4. Added an automated payment-decline E2E path and isolated serial cart state between checkout tests.

## Unresolved defects and limitations

- The host has Node 16.14.2 while the frontend declares Node 22. npm install, build, and E2E passed
  with engine warnings; use the declared Node 22 toolchain for normal development and CI.
- `helm` is not installed in the validation environment. `kubectl kustomize` rendered successfully,
  but client dry-run could not validate resources because no usable Kubernetes API/context was
  available. No live cluster changes were made.
- Full `npm audit` includes known moderate/high/critical vulnerabilities in development/tooling
  transitive packages; the production-only audit reported zero vulnerabilities. These are tracked
  as toolchain risk and were not upgraded blindly during release validation.
- Service containers emit low-severity OTLP metrics connection warnings to `localhost:4318` while
  Prometheus metrics remain available. This is an observability configuration warning, not a failed
  business flow.
- No hosted capacity, production disaster recovery, or real payment/email/shipping-provider testing
  was performed; all providers remain local/fake by design.

## API compatibility status

Existing gateway/service API contracts used by the UI remained compatible. The release fixes are
client cache/state handling and a repeatable local fixture; no public API contract was removed or
changed.

## Event compatibility status

Existing order, inventory, payment, outbox, inbox, notification, and fulfillment event flows were
exercised through the local Compose environment. Duplicate-event and callback behavior remains
idempotent per the prior hardening evidence; no event schema changes were introduced by this
milestone.

## Migration status

`./mvnw clean verify` passed all module migrations and integration checks. PostgreSQL backup and
restore validation covered all nine local service databases.

## Security status

No credentials, card data, access tokens, certificates, or generated artifacts were added. Maven
Checkstyle/SpotBugs, existing secret/container/Kubernetes scans, and the production npm audit passed
where executable. The remaining development-tool audit findings and Node-version mismatch are
documented above.

## Performance observations

The release candidate was validated for correctness and local integration, not production capacity.
Prior Milestone 7 local load reports remain the authoritative performance evidence; no new hosted or
large-scale benchmark is claimed here.

## Observability status

Local Prometheus/Grafana/Tempo/OpenSearch smoke checks passed and correlation logging was observed.
The OTLP metrics endpoint warning described above should be cleaned up before any hosted deployment.

## Release recommendation

**CONDITIONAL READY FOR LOCAL RELEASE CANDIDATE DEMONSTRATION.** Merge only after review of the
documented environment limitations. Do not treat this candidate as production-ready until the Node
22 toolchain is used, Helm/Kubernetes validation runs against a real local cluster, development
dependency findings are triaged, and observability export configuration is corrected.
