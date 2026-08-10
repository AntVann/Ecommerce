# System Overview

MarketFlow is a multi-vendor marketplace implemented as independently owned bounded contexts. The local runtime is a Docker Compose profile containing application services and free/open-source dependencies. Kubernetes and Helm resources provide an optional local deployment shape.

## Context

`mermaid
flowchart TB
    customer[Customer] --> ui[React UI]
    sellerUser[Seller] --> ui
    admin[Administrator] --> ui
    ui --> api[REST APIs]
    api --> contexts[Identity / Seller / Catalog / Search / Inventory / Cart / Order / Payment / Notification]
    contexts --> data[(Service-owned data stores)]
    contexts --> events[Kafka domain events]
    contexts --> tasks[RabbitMQ task queues]
    contexts --> telemetry[Logs / Metrics / Traces]
`

The UI is a client of the APIs, not a second authorization layer. Search is a projection and never the catalog source of truth. Cart prices are advisory and checkout revalidates current state.

## Runtime boundaries

- Synchronous calls use versioned REST/OpenAPI contracts.
- Durable domain facts use versioned Kafka events.
- Task-oriented notification delivery uses RabbitMQ with retries and DLQs.
- Each service owns its persistence and migrations.
- Cross-context changes use events and compensating workflows rather than distributed transactions.
- Local credentials in Compose are development placeholders only.

## Source of truth

The approved hierarchy is ADRs, contracts, this engineering plan, then code. Start with docs/adr, contracts/, and docs/engineering-plan.md when investigating behavior.
