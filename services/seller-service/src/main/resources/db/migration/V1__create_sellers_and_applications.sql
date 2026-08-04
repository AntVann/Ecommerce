CREATE TABLE seller (
    id UUID PRIMARY KEY,
    applicant_user_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    country_code CHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_seller_active_application
    ON seller(applicant_user_id) WHERE status IN ('PENDING_REVIEW', 'APPROVED', 'SUSPENDED');

CREATE TABLE seller_application (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL UNIQUE REFERENCES seller(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    decision_reason VARCHAR(500)
);

CREATE TABLE seller_status_history (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES seller(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    actor_user_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_seller_status_history ON seller_status_history(seller_id, occurred_at DESC);
