CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),
    failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until TIMESTAMPTZ,
    token_invalid_before TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);

CREATE TABLE credential (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    password_hash VARCHAR(512) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE email_verification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'ISSUED', 'CONSUMED', 'CANCELLED')),
    token_digest CHAR(64) UNIQUE,
    expires_at TIMESTAMPTZ,
    issued_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_email_verification_active_user
    ON email_verification(user_id) WHERE status IN ('QUEUED', 'ISSUED');
CREATE INDEX ix_email_verification_user ON email_verification(user_id, created_at DESC);
