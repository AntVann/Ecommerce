# Messaging Architecture

## Why two brokers?

Kafka is used for durable, replayable domain facts. It supports versioned event streams, aggregate-key ordering, consumer groups, lag monitoring, and projection rebuilds.

RabbitMQ is used for task commands such as notification delivery. Explicit acknowledgement, bounded retries, per-queue routing, and dead-letter handling make worker tasks operationally clear.

This division follows ADR-005 and ADR-006; it is not an attempt to make either broker the universal transport.

## Kafka flow

`mermaid
flowchart LR
    producer[Service transaction] --> outbox[(Transactional outbox)]
    outbox --> relay[Publisher]
    relay --> topic[(Versioned Kafka topic)]
    topic --> consumer[Consumer]
    consumer --> inbox[(Processed-message inbox)]
    consumer --> effect[Idempotent business effect]
    topic --> projection[Projection / analytics consumer]
`

Topic names and schemas are declared in docker-compose, contracts/asyncapi/marketflow.yaml, and contracts/events. Event IDs, aggregate versions, correlation IDs, and schema versions are retained.

## RabbitMQ flow

`mermaid
flowchart LR
    order[Order event consumer] --> exchange[Notification exchange]
    exchange --> queue[Work queue]
    queue --> worker[Notification worker]
    worker -->|transient failure| retry[Retry route]
    retry --> queue
    worker -->|terminal failure| dlq[Dead-letter queue]
`

Consumers acknowledge only after the idempotent effect is recorded. Inspect and redrive procedures are in docs/runbooks/dead-letter.md and docs/runbooks/notification-fulfillment-local.md.

## Compatibility

Additive fields are compatible. Renamed, removed, or semantically changed fields require a new version and compatibility review. See ADR-019 and the schemas in contracts/.

