# Database migration procedure

MarketFlow databases remain service-owned. Migrations must be additive first, tolerate mixed
application versions, and run once per service with a least-privilege migration identity.

For local Compose, Flyway startup migration is supported. For staging-like Kubernetes rehearsals,
use an explicitly enabled, reviewed Job per service and verify the Job completion before rollout.
Migration Jobs must not contain credentials or destructive commands in Git. A failed migration
blocks deployment; do not delete the database or volume to recover.
