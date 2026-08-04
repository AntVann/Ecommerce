# ADR-006: RabbitMQ for task queues
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Some asynchronous work must be handled by one worker with explicit acknowledgement and retry routing.
## Decision
Use RabbitMQ for task commands such as notification delivery and report generation.
## Alternatives considered
Kafka consumer groups can distribute work but provide a less direct task retry/DLQ model.
## Consequences
Jobs are idempotent, bounded in retries, and route terminal failures to named dead-letter queues.
## Security implications
Use least-privilege virtual-host permissions and never place secrets in messages.
## Operational implications
Queue depth, oldest age, consumer health, retries, and DLQ size are monitored.
## Migration / rollback
Queue bindings support side-by-side workers; rollback preserves compatible job payload versions.
