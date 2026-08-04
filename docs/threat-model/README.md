# Foundation Threat Model

## Assets and trust boundaries

Protected assets include credentials, tokens, customer and seller data, commercial snapshots,
inventory correctness, payment state, audit history, and operational secrets. Browser traffic
crosses the internet boundary only through the gateway. Internal service, broker, database, object
storage, and observability connections remain private and authenticated in deployed environments.

## Foundation threats and controls

| Threat | Milestone 0 control |
|---|---|
| Secrets committed or logged | `.env` ignored, examples contain local-only placeholders, CI secret scan, structured-log policy |
| Dependency compromise | Pinned direct build/tool versions, dependency automation, dependency and image scanning stages |
| Untrusted correlation input | Length and character allow-list; invalid values replaced; safe response propagation |
| Exposed management endpoints | Only health/info/Prometheus enabled in the sample; deployment must restrict network access |
| Container privilege escalation | Non-root runtime user, minimal JRE image, read-only-compatible application layout |
| Cross-service data access | Repository and ADR rules prohibit shared databases and persistence entities |
| Telemetry data leakage | Safe attribute policy; tokens, credentials, payment data, and addresses prohibited |

Feature-specific threat modeling and authorization tests are required as business capabilities are
introduced. The sample service and local credentials are not production deployables.

