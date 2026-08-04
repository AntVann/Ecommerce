CREATE TABLE seller_membership (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES seller(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role_code VARCHAR(32) NOT NULL CHECK (role_code IN ('OWNER', 'MANAGER', 'STAFF')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    UNIQUE (seller_id, user_id)
);

CREATE INDEX ix_seller_membership_user ON seller_membership(user_id, seller_id);

CREATE TABLE seller_role_permission (
    role_code VARCHAR(32) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
);

INSERT INTO seller_role_permission(role_code, permission_code) VALUES
    ('OWNER', 'SELLER_PROFILE_READ'),
    ('OWNER', 'SELLER_MEMBER_READ'),
    ('OWNER', 'SELLER_MEMBER_MANAGE'),
    ('MANAGER', 'SELLER_PROFILE_READ'),
    ('MANAGER', 'SELLER_MEMBER_READ'),
    ('STAFF', 'SELLER_PROFILE_READ');
