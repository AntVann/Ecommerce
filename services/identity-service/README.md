# MarketFlow Identity Service

Owns customer accounts, credentials, verification challenges, coarse global roles, signed access
tokens, rotating refresh-token families, revocation state, rate limits, security events, and
Identity outbox records. Raw passwords and tokens must never be stored or logged.

The one-time internal verification-token claim endpoint is the minimal delivery boundary for the
future Notification service. It returns a raw token only once and stores only its digest.
