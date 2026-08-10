# Events and Message Contracts

## Sources

- contracts/asyncapi/marketflow.yaml defines the asynchronous channel model.
- contracts/events contains versioned domain-event JSON Schemas.
- contracts/messages contains RabbitMQ task-message schemas.
- contracts/events/README.md explains the shared envelope and examples.

## Kafka domain events

Events include immutable event IDs, aggregate IDs/versions, event type/version, occurred time, correlation ID, and bounded payloads. Kafka is used for durable facts and rebuildable projections. Producers write an outbox row with the local transaction; consumers use inbox deduplication.

Representative event families:

- identity user registration/disablement
- seller approval/rejection/suspension
- catalog publication/update/deactivation/price change
- inventory adjustment/reservation/release
- order creation/confirmation/payment failure/shipment
- payment authorization/decline/failure/unknown

## RabbitMQ task messages

Notification commands are task-oriented and use explicit acknowledgement, retry routing, and dead-letter queues. The fake email provider is local and records delivery attempts; notification failure does not cancel a confirmed order.

## Compatibility rules

Additive fields are compatible. Renames, removals, or meaning changes require a new version and contract review. Consumers must tolerate duplicate delivery and retry after restart. Never place passwords, access tokens, card numbers, or provider secrets in messages.

Use docs/runbooks/dead-letter.md and docs/runbooks/outbox-backlog.md for operations.

