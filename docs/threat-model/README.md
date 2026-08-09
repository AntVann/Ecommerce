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

## Identity and Seller threats

| Threat | Milestone 1 control |
|---|---|
| Credential database disclosure | Argon2id adaptive hashes with unique salts; raw passwords never persisted or logged |
| Account enumeration | Equivalent registration behavior and generic authentication failures |
| Credential stuffing and brute force | Redis-backed account and source limits, temporary lock window, audit event, `Retry-After` |
| Refresh-token theft or replay | Random opaque values, digest-only storage, atomic rotation, family-wide reuse revocation |
| Access-token forgery or stale privilege | RS256, `kid`/JWKS, issuer/audience/time validation, short expiry, live sensitive-operation check |
| Cookie request forgery | Secure HttpOnly SameSite refresh cookie and double-submit CSRF validation on refresh/logout |
| Disabled principal continues operating | Session-family revocation, issued-before timestamp, JTI revocation, live Identity check |
| Customer invokes administrator operation | Coarse JWT role check plus live Identity role confirmation |
| Seller crosses tenant boundary | Membership lookup includes both authenticated user and seller ID; protected probes return 404 |
| Staff changes seller membership | Only OWNER membership has `SELLER_MEMBER_MANAGE`; owner cannot be removed through staff API |
| Sensitive telemetry | No passwords, raw tokens, email addresses, or free-text reasons in metrics or event payloads |
| Administrative repudiation | Append-only security events and seller status history record actor, reason, time, and correlation |

The Compose signing key is ephemeral and local-development-only. Production key management,
service workload identity, and network policy are deferred to the cloud deployment milestone.

## Catalog, Inventory, and Search threats

| Threat | Milestone 2 control |
|---|---|
| Seller modifies another tenant's product or stock | Live Seller permission decision plus seller ID in every ownership query; protected probes return 404 |
| Suspended seller remains publicly visible | Version-aware Seller projections remove public Catalog/Search visibility |
| Duplicate or conflicting SKU | Canonical seller-scoped database uniqueness constraint |
| Floating-point price corruption | `BigDecimal`, ISO currency validation, and `NUMERIC(19,4)` persistence |
| Oversell or negative stock | Database check constraints and conditional atomic reservation/adjustment SQL |
| Duplicate command or event | Idempotency records, processed-message inboxes, version-aware projections, and idempotent OpenSearch writes |
| Product image path injection | Seller/product-scoped object keys, content-type allow-list, size and dimension constraints; bytes remain in object storage |
| Search becomes an authority | Direct detail remains Catalog-owned and Search can be deleted/rebuilt through an alias switch |
| Internal API spoofing | Constant-time service-key comparison in local runtime; workload identity remains the production target |

## Cart, Checkout, and Order threats

| Threat | Milestone 3 control |
|---|---|
| Guest cart takeover | High-entropy opaque guest credential, digest-only Redis keying, secure cookie attributes, and CSRF validation on mutations |
| Customer reads or mutates another cart | Cart identity is derived from the validated access token; caller-supplied customer IDs are not trusted |
| Stale or manipulated cart price | Cart price is advisory; checkout obtains current Catalog money values and snapshots the validated result |
| Disabled seller or unavailable stock reaches an order | Checkout revalidates Seller state and Inventory availability through protected owner APIs |
| Duplicate checkout creates multiple orders | Idempotency key plus canonical request fingerprint and database uniqueness return one durable result |
| Duplicate event reserves stock twice | Inventory and Order persist event IDs in service-owned inboxes with the business transition |
| Partial checkout loses an integration event | Order and Inventory persist domain state and outbox rows in the same local transaction |
| Negative stock during concurrent checkout | Inventory uses conditional updates, deterministic lock order, and database constraints |
| Address or cart data leaks through telemetry | Structured logs contain identifiers and outcomes only; addresses, tokens, cookies, and item payloads are excluded |
| Reservation outcome never arrives | Saga timeout and reservation expiry produce explicit cancellation or manual-review behavior and metrics |

## Payment and Order Completion threats

| Threat | Milestone 4 control |
|---|---|
| Real payment credentials enter the simulator | Strict `mf_fake_*` token allow-list; card-like values are rejected and no compliance claim is made |
| Ambiguous timeout causes a second authorization | Payment remains `UNKNOWN`; reconciliation reuses the original provider idempotency key |
| Duplicate or replayed callback repeats a transition | Signed callbacks, unique callback IDs, aggregate guards, and atomic inbox/outbox persistence |
| Forged internal authorization | Constant-time service-key validation and customer/order ownership verification at Order |
| Authorized stock expires or is released | Order confirms only after an idempotent Inventory commitment; contradictory facts enter manual review |
| Declined payment leaves stock held | Release compensation precedes `PAYMENT_FAILED` and retries idempotently |
| Customer or seller reads another order | Customer ownership and seller membership plus seller-line filtering are enforced in repository queries |
| Payment token or provider payload leaks | Tokens are excluded from events, persistence outside Payment, responses, structured logs, traces, and metrics |
