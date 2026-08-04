# ADR-001: Java 21 and Spring Boot 3
Status: Superseded by ADR-022
Date: 2026-08-03
Owners: MarketFlow Architecture

## Context
The platform needs a supported Java baseline with mature security, data, telemetry, and test integrations.
## Decision
Use Java 21 LTS and the supported Spring Boot 3.5 line, pinned by the Maven parent.
## Alternatives considered
Java 17 offers fewer current language/runtime capabilities; Spring Boot 4 conflicts with the approved 3.x baseline.
## Consequences
All services share the toolchain but remain independently deployable. Upgrades are centralized and verified.
## Security implications
Patch releases must be applied promptly and dependency scans gate releases.
## Operational implications
Images and CI runners must provide Java 21.
## Migration / rollback
Patch upgrades are reversible through the parent version; major upgrades require a new ADR.
