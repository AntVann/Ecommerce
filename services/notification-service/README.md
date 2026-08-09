# MarketFlow Notification Service

Notification delivery is an eventually consistent bounded context. Kafka order facts are deduplicated in `notification_inbox`, then persisted as notification jobs and an outbox command. The outbox publishes to durable RabbitMQ queues. A fake email provider supports `success`, `transient-failure`, `permanent-failure`, and `timeout` scenarios through `FAKE_EMAIL_SCENARIO`.

Jobs and attempts are idempotent. Transient failures use bounded exponential backoff and are republished only when `next_attempt_at` is due. Exhausted or permanent failures are marked `DEAD_LETTERED`; Rabbit dead-letter queue inspection and redrive must be performed by an authorized operator using the job ID and correlation ID. Notification failure never mutates order state.

Local defaults use PostgreSQL on port 5440, RabbitMQ on 5672, Kafka on 29092, and HTTP on 8089. No provider credentials or unrestricted customer data are required or logged.
