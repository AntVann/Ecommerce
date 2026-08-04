# ADR-016: Simulated payment provider for MVP
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
The portfolio must demonstrate payment correctness without handling regulated card data.
## Decision
Accept only opaque fake tokens and use a simulator for approve, decline, timeout, delay, and duplicate callback outcomes.
## Alternatives considered
A real provider expands compliance and operational scope; a success-only stub cannot test ambiguity or compensation.
## Consequences
Payment logic uses a provider anti-corruption layer and never assumes a timeout is a decline.
## Security implications
Real card numbers and security codes are rejected, never stored, and never logged; no PCI claim is made.
## Operational implications
Simulator scenarios are deterministic, observable, and usable in failure tests.
## Migration / rollback
A future provider implements the same port and requires a new security/compliance decision.

