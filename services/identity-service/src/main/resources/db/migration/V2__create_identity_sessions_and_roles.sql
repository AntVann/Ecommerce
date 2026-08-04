CREATE TABLE refresh_token_family (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED', 'COMPROMISED')),
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(64)
);

CREATE INDEX ix_refresh_family_user_status ON refresh_token_family(user_id, status);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES refresh_token_family(id) ON DELETE CASCADE,
    token_digest CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CURRENT', 'ROTATED', 'REVOKED')),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_token(id)
);

CREATE INDEX ix_refresh_token_family ON refresh_token(family_id, issued_at DESC);

CREATE TABLE role_assignment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    role_code VARCHAR(32) NOT NULL CHECK (role_code IN ('CUSTOMER', 'ADMIN')),
    granted_at TIMESTAMPTZ NOT NULL,
    granted_by UUID,
    UNIQUE (user_id, role_code)
);

CREATE TABLE access_token_revocation (
    token_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(64) NOT NULL
);

CREATE INDEX ix_access_token_revocation_expiry ON access_token_revocation(expires_at);
