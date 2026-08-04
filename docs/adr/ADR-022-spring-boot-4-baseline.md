# ADR-022: Spring Boot 4 baseline

Status: Accepted
Date: 2026-08-04
Owners: MarketFlow Architecture

## Context

The dependency maintenance batch merged Spring Boot 4.1 into `main` before Milestone 2. Reverting
new business services to the obsolete 3.x parent would create two unsupported framework baselines
inside the monorepo.

## Decision

Keep Java 21 as the application language baseline and use the repository-pinned Spring Boot 4.1
parent. CI accepts compatible JDKs from 21 through 25, while production images currently use the
pinned Java 25 runtime. Framework-major upgrades remain centralized in the root Maven parent.

## Consequences

Milestone 2 uses Spring Boot 4 package locations and conventions. All modules continue to compile
for Java 21 bytecode and remain independently deployable. ADR-001 is superseded.

## Security and operations

Dependency, source, image, and secret scanning remain release gates. Framework upgrades require a
complete Maven verification, contract validation, and Compose smoke run.

## Rollback

A rollback requires a repository-wide compatibility branch and cannot mix Spring Boot major
versions between modules.
