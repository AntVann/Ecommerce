# MarketFlow Architecture

The system architecture is defined by `docs/engineering-plan.md` and the accepted ADRs in
`docs/adr`. Public contracts under `contracts/` take precedence over prose when implementation
begins.

## Non-negotiable boundaries

- Each deployable business service owns one bounded context, its invariants, and its persistence.
- Services do not query another service database or share persistence entities.
- API code depends on application code; application code depends on domain code; infrastructure
  implements ports owned by the inner layers.
- Synchronous calls are explicit and bounded. Cross-service state changes use versioned events and
  compensating workflows rather than distributed transactions.
- Retry-prone commands and every event consumer are idempotent.
- Logs, metrics, traces, error codes, and failure behavior are designed with each capability.

The Milestone 0 sample service is intentionally not a bounded context. It proves platform
conventions and must not accumulate business behavior.

