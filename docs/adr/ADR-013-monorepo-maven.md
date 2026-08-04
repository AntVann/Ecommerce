# ADR-013: Monorepo and Maven parent
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Contracts, services, tests, and platform automation must evolve together without losing ownership boundaries.
## Decision
Use one repository with a Java 21 Maven parent, independent service modules, and repository-wide quality gates.
## Alternatives considered
Multiple repositories add coordination overhead before teams require independent governance.
## Consequences
CODEOWNERS and paths express ownership; shared build convention does not imply shared domain models.
## Security implications
Central scans and dependency management reduce drift while service secrets remain isolated.
## Operational implications
CI detects changed paths but can run the full reactor for compatibility.
## Migration / rollback
Services may split repositories later without changing their contracts or data ownership.

