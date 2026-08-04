# ADR-019: API and event compatibility and versioning
Status: Accepted
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
Independent producers and consumers deploy at different times and must tolerate mixed versions.
## Decision
Version contracts, permit additive optional fields, tolerate unknown fields, and gate breaking changes in CI.
## Alternatives considered
Coordinated lockstep deployment weakens independence; reinterpretation silently corrupts consumers.
## Consequences
Breaking API or event changes require a new version, migration window, ownership review, and ADR when architectural.
## Security implications
Compatibility never permits weaker authorization, secret exposure, or unsafe validation.
## Operational implications
Old and new versions may coexist; usage and deprecation are observable.
## Migration / rollback
Deploy consumers before additive producers and retain old handlers until usage reaches zero.
