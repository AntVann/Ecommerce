# Local chaos and recovery runbook

Run `scripts/chaos-local.ps1` only against a disposable local Compose environment. It stops and
restarts Redis, Kafka, RabbitMQ, Identity PostgreSQL, and Notification without deleting volumes.

For each drill capture the trigger, readiness response, outbox/lag/queue state, recovery time,
duplicate-event behavior, and final business invariant. Expected outcomes are documented in
`tests/chaos/README.md` and `docs/engineering-plan.md`.

Never use `docker compose down -v`, delete a database, or replay production messages as part of a
local drill.
