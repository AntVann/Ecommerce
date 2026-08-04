CREATE TABLE security_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    actor_user_id UUID,
    subject_user_id UUID,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),
    reason_code VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    source_hash CHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_identity_security_event_subject_time
    ON security_event(subject_user_id, occurred_at DESC);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_identity_outbox_pending
    ON outbox_event(next_attempt_at, occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE idempotency_record (
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (operation, idempotency_key)
);
