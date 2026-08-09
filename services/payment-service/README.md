# Payment Service

Owns simulated payment authorization, attempts, provider callbacks, reconciliation, and payment events for the checkout Saga.

## Safety boundary

This service accepts only the documented opaque fake tokens. It does not accept, persist, log, or transmit card numbers or security codes. It is a simulator and makes no payment-compliance certification claim.

Supported scenarios are `mf_fake_approve`, `mf_fake_decline`, `mf_fake_timeout`, `mf_fake_delayed_approve`, `mf_fake_delayed_decline`, and `mf_fake_duplicate`. Callback delay, duplicate callback count, provider availability, and callback signature are configurable.

## Internal API

`POST /internal/v1/payments/authorizations` requires `X-Internal-Service-Key` and `Idempotency-Key`. Reusing a key with the same request returns the original payment; changing the request is rejected. An ambiguous timeout becomes `UNKNOWN`; it is reconciled by idempotency key and is never blindly authorized again. Three unresolved reconciliation checks mark the payment for manual review.

Fake provider callbacks use `POST /internal/v1/payments/callbacks/fake`, the internal service key, and `X-Fake-Provider-Signature`. Callback event IDs are deduplicated.

Payment outcomes are written atomically to `outbox_event` and relayed to `marketflow.payment.events.v1`. No fulfillment or notification delivery is implemented here.
