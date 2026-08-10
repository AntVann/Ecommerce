# Security Architecture

## Authentication

Identity owns password hashes, account state, verification, access-token signing keys, refresh-token families, revocation, login rate limits, and security events. Access tokens are short-lived; refresh tokens are opaque, rotated, and invalidated on reuse or logout.

## Authorization

- Roles are checked at protected endpoints.
- Service methods enforce resource ownership and seller tenant scope.
- Seller services validate seller membership and permissions.
- Customers can access only their own carts and orders.
- Administrative operations require administrative roles.
- Internal endpoints are not intended to be internet-routable.

## Data handling

- Passwords, access tokens, refresh tokens, fake payment tokens, and secrets are not logged.
- Real card numbers are never accepted or persisted.
- Local Compose values are placeholders and must not be reused outside an isolated development environment.
- Event payloads are minimized and use opaque identifiers where possible.
- Audit events record security-sensitive decisions without credential material.

## Evidence and limitations

The threat model is in docs/threat-model/README.md and the hardening assessment is in docs/security/milestone-07-assessment.md. Those documents contain the verified scan scope and limitations. This repository makes no PCI, SOC 2, or production security certification claim.
