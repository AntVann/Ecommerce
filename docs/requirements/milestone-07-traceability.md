# Milestone 7 requirements traceability

This matrix is the release-readiness index for hardening. It links every required evidence class to
the command, test, document, or accepted limitation that proves it. A row is not marked PASS until
the evidence is actually produced on the milestone branch.

| Requirement | Evidence location | Status before M7 | M7 evidence |
|---|---|---|---|
| Browse/search/cart/checkout load | `tests/performance/` | Not present | Search, cart, and checkout k6 runs passed; browse is BLOCKED without a published fixture |
| Concurrent low-stock checkout | Inventory/order tests and k6 scenario | Unit/integration coverage exists | Maven concurrency tests passed; k6 safety run passed, but no low-stock fixture was loaded |
| Kafka and consumer recovery | `tests/chaos/`, local Compose | Manual behavior exists | Stop/restart recovery drill passed for Kafka and order readiness |
| Notification backlog/DLQ | `docs/runbooks/dead-letter.md` | Retry/DLQ behavior exists | Runbook added; backlog injection/redrive was not executed (BLOCKED) |
| Query plans and index tuning | `docs/performance/milestone-07-report.md` | Not documented | Local EXPLAIN evidence captured; existing indexes were sufficient |
| Authentication/authorization/isolation | service tests and `docs/security/` | Implemented through M5 | Maven regression, negative-path, and isolation tests passed |
| Sensitive-data logging | Structured logging policy and scans | Implemented by convention | Redaction review completed; no credentials or payment data logged |
| Dependencies/secrets/SAST/images/Kubernetes | `.github/workflows/ci.yml`, platform manifests | Partial | Gitleaks, npm audit, Maven static checks, Trivy image/config scans, and schema validation passed; CodeQL deferred to CI |
| Backup/restore/DR | `scripts/backup/`, `docs/runbooks/backup-restore.md` | Not demonstrated | Nine PostgreSQL dumps were restored into disposable containers successfully |
| Alerts and runbooks | `platform/observability/alert-rules.yml`, `docs/runbooks/` | Dashboards exist | Prometheus found 8 valid rules; recovery and backup runbooks added |
| Final API/event documentation | `contracts/`, `docs/api-event-index.md` | Contracts exist | Node 22 contract lint passed with 32 non-blocking existing warnings |
| Final demonstrations | `docs/demo/milestone-07-demo.md` | Not scripted | Script added; successful authenticated checkout and duplicate-event demos remain BLOCKED |

The final completion report must replace every provisional status with PASS, BLOCKED, or an
explicitly accepted limitation.
