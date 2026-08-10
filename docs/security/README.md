# Security Documentation

## Security model

Identity authenticates users and owns sessions. Seller, Catalog, Inventory, Cart, Order, Payment, and Notification enforce authorization at their own service boundary. Seller resources are scoped to the seller membership and permission of the authenticated actor.

## Protected data rules

- Hash passwords; never store plaintext credentials.
- Rotate and revoke refresh-token families.
- Never log credentials, tokens, fake payment tokens, or sensitive address/payment details.
- Accept only opaque fake payment tokens; do not accept card numbers.
- Use local placeholder secrets only in isolated development.
- Keep internal endpoints off the public route.
- Minimize event payloads and audit security decisions.

## Evidence

- Threat model: docs/threat-model/README.md
- Hardening assessment: docs/security/milestone-07-assessment.md
- Secret rotation: docs/runbooks/secret-rotation.md
- Relevant ADRs: 012, 019, 020, and 021

The repository does not claim PCI, SOC 2, or other compliance certification. Scan results and limitations are recorded in the release evidence rather than inferred here.

