# MarketFlow release readiness checklist

- [x] Milestone 6 is merged into `origin/main`.
- [x] Clean checkout builds Java, contracts, images, and Compose.
- [x] All required M7 load scenarios have captured results against disposable local fixtures.
- [x] Concurrent final-unit safety is demonstrated by the existing concurrency tests plus a one-unit fixture: one reservation succeeds and the next customer receives HTTP 409 without negative stock.
- [x] Kafka, consumer, Redis, database, and notification recovery drills pass.
- [x] Backup and disposable restore validation pass.
- [x] Threat model and authentication/authorization review are current.
- [x] Secret, dependency, static, filesystem, image, and Kubernetes scans have no unapproved
  critical/high findings.
- [x] Query plans and any index/JVM changes are evidence-based.
- [x] Alert rules, dashboards, DLQ, outbox, database, and incident runbooks are documented; a malformed-message DLQ inspect/redrive drill was executed and the invalid message was rejected safely.
- [x] API and event compatibility checks pass.
- [x] Success, failed-payment compensation, duplicate callback, low-stock rejection, and observability demonstrations were executed through the local API and Compose services.
- [x] Local-only/free infrastructure boundary is documented.
- [x] No secrets, generated reports, dumps, or raw load-test output are committed.

Final status for this branch is `CONDITIONAL`: local hardening evidence and fixture-backed
demonstrations pass, while production-scale capacity, hosted recovery, and CI-provided CodeQL
remain outside this free local environment.
