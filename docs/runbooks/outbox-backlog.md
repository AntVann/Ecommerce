# Outbox backlog runbook

1. Identify the owning service and oldest unpublished event.
2. Confirm database readiness, broker connectivity, relay health, retry count, and next-attempt
   time.
3. Verify that the event is not already applied by an idempotent consumer before replaying it.
4. Restore the broker or service dependency and allow the relay to drain naturally.
5. Alert on sustained age, repeated failures, or a growing backlog.
6. Record event ID, aggregate ID, correlation ID, and outcome without copying payload secrets.

Never delete an outbox row to clear an alert. Use a documented, audited remediation or forward
recovery path.
