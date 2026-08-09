CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(24) NOT NULL CHECK (status IN ('CREATED','PROCESSING','AUTHORIZED','DECLINED','FAILED','UNKNOWN')),
    reason_code VARCHAR(80),
    manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_attempts INTEGER NOT NULL DEFAULT 0 CHECK (reconciliation_attempts >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_customer_created ON payment(customer_id, created_at DESC);
CREATE INDEX idx_payment_status_updated ON payment(status, updated_at);

CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    idempotency_key VARCHAR(128) NOT NULL,
    provider_reference VARCHAR(128),
    status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE(payment_id, attempt_number),
    UNIQUE(idempotency_key)
);

CREATE UNIQUE INDEX idx_payment_attempt_provider_reference
    ON payment_attempt(provider_reference) WHERE provider_reference IS NOT NULL;

CREATE TABLE provider_callback (
    provider_event_id UUID PRIMARY KEY,
    provider_reference VARCHAR(128) NOT NULL,
    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('AUTHORIZED','DECLINED','FAILED')),
    reason_code VARCHAR(80),
    received_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_record (
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    payment_id UUID REFERENCES payment(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(operation, idempotency_key)
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_outbox_pending
    ON outbox_event(next_attempt_at, occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(consumer_name, event_id)
);

COMMENT ON TABLE payment IS 'Opaque-token payment state; card numbers and security codes are prohibited';
COMMENT ON TABLE payment_attempt IS 'Provider attempts without payment token or credential persistence';
