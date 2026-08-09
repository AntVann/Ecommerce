# Dead-letter and redrive runbook

1. Identify the queue/topic, consumer, message ID, correlation ID, and failure reason.
2. Inspect the payload for schema or authorization defects without exposing sensitive values.
3. Correct the consumer or contract before replaying anything.
4. Redrive one message in a disposable local environment first.
5. Confirm inbox deduplication and business invariants.
6. Redrive in bounded batches and monitor retry/DLQ depth.

Notification DLQ messages never cancel a confirmed order. Payment or inventory contradictions are
manual-review cases, not blind replays.
