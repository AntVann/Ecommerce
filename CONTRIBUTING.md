# Contributing to MarketFlow

Read `docs/engineering-plan.md`, applicable ADRs, public contracts, and the owned service README
before changing code.

## Workflow

1. Work on the branch assigned to the active milestone.
2. Keep bounded-context ownership and database ownership intact.
3. Use conventional commits with a meaningful scope.
4. Update contracts and documentation with behavior changes.
5. Run `make verify` or the equivalent PowerShell commands before committing.
6. Never commit `.env`, credentials, tokens, real payment data, or sensitive personal data.

Public API, event, security-boundary, or service-ownership changes require contract review and an
ADR when they change an approved architectural decision.

## Definition of done

Changes must include the applicable unit, integration, contract, authorization, concurrency, and
failure-path tests. Logs, metrics, traces, stable error codes, migration behavior, and operational
documentation are part of the feature—not follow-up work.
