# ADR-020: Secrets and workload identity strategy
Status: Accepted  
Date: 2026-08-03  
Owners: MarketFlow Architecture

## Context
Static credentials in Git, images, or broadly shared configuration are difficult to rotate and audit.
## Decision
Use developer-managed local values, encrypted CI secrets, and cloud workload identity with external secret references.
## Alternatives considered
Committed environment files and long-lived shared service tokens create unacceptable exposure.
## Consequences
Configuration is externalized, validated, least-privilege, rotatable, and absent from logs and artifacts.
## Security implications
Secret scanning gates CI; service and database identities are audience- and owner-scoped.
## Operational implications
Rotation, access, expiry, and compromise response are documented and auditable.
## Migration / rollback
Credentials overlap only for a bounded rotation window; compromised values are revoked rather than restored.
