# MarketFlow Architecture

Start with the [system overview](system-overview.md), then read [services](services.md), [data ownership](data-ownership.md), [messaging](messaging.md), and [checkout Saga](checkout-saga.md).

Cross-cutting guides:

- [Security](security.md)
- [Observability](observability.md)
- [Local Kubernetes](../deployment/local-kubernetes.md)
- [Architecture decisions](../adr/)

The system is organized around independently owned bounded contexts. Services integrate through versioned REST contracts and domain events; they do not share persistence entities or databases. The accepted decisions in docs/adr and normative schemas in contracts/ take precedence over explanatory prose.
