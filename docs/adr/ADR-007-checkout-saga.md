# ADR-007: Orchestrated Saga for checkout
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Checkout spans order, inventory, and payment ownership without a shared transaction.
## Decision
The Order service orchestrates checkout state and explicit compensations.
## Alternatives considered
Distributed transactions conflict with independent stores; choreography obscures authority and terminal state handling.
## Consequences
Every transition, timeout, compensation, and manual-review path is recorded and tested.
## Security implications
Commands carry authenticated identity context and opaque payment tokens only.
## Operational implications
Saga state, duration, failures, and manual-review counts are dashboarded and alerted.
## Migration / rollback
Workflow versions coexist for in-flight orders; deployments retain handlers for active versions.
