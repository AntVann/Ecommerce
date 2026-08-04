# Event compatibility

The versioned event envelope is common transport metadata. Domain event `data` remains owned by
the producing bounded context and receives its own versioned schema before implementation.

- Event names are past-tense facts: `domain.entity-event.v1`.
- `aggregateId` is the Kafka partition key when aggregate ordering matters.
- Unknown fields are compatible and must be tolerated.
- Existing fields are never removed, renamed, or reinterpreted within a version.
- Producers write events through a transactional outbox.
- Consumers store `(consumerName, eventId)` with the business mutation in one local transaction.
- Payloads minimize or redact personal and sensitive information.

