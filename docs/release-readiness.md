# MarketFlow release readiness checklist

- [x] Milestone 6 is merged into `origin/main`.
- [x] Clean checkout builds Java, contracts, images, and Compose.
- [!] All required M7 load scenarios have captured results (browse is blocked by missing fixture).
- [!] Concurrent final-unit checkout proves no oversell (existing concurrency tests pass; no local low-stock fixture was loaded).
- [x] Kafka, consumer, Redis, database, and notification recovery drills pass.
- [x] Backup and disposable restore validation pass.
- [x] Threat model and authentication/authorization review are current.
- [x] Secret, dependency, static, filesystem, image, and Kubernetes scans have no unapproved
  critical/high findings.
- [x] Query plans and any index/JVM changes are evidence-based.
- [!] Alert rules, dashboards, DLQ, outbox, database, and incident runbooks are documented; DLQ injection/redrive was not executed.
- [x] API and event compatibility checks pass.
- [!] Success, failed-payment compensation, duplicate-event, and observability demonstrations are scripted; authenticated end-to-end replay remains blocked in this API-only local run.
- [x] Local-only/free infrastructure boundary is documented.
- [x] No secrets, generated reports, dumps, or raw load-test output are committed.

Final status for this branch is `CONDITIONAL`: hardening checks that were executable passed; the
fixture-dependent demonstrations and DLQ replay remain explicitly blocked rather than being
represented as successful evidence.
