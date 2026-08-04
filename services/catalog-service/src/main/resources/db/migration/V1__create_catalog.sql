CREATE TABLE category (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES category(id),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES category(id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')),
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX ix_product_seller ON product(seller_id, updated_at DESC);

CREATE TABLE product_variant (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL,
    sku VARCHAR(80) NOT NULL,
    canonical_sku VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    price_amount NUMERIC(19,4) NOT NULL CHECK (price_amount >= 0),
    price_currency CHAR(3) NOT NULL CHECK (price_currency ~ '^[A-Z]{3}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (seller_id, canonical_sku)
);
CREATE INDEX ix_variant_product ON product_variant(product_id);

CREATE TABLE product_image (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    alt_text VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','READY','REJECTED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE catalog_status_history (
    id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    previous_status VARCHAR(16), new_status VARCHAR(16) NOT NULL,
    actor_user_id UUID NOT NULL, correlation_id VARCHAR(128) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY, event_type VARCHAR(120) NOT NULL, aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL, aggregate_version BIGINT NOT NULL, correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_catalog_outbox_pending ON outbox_event(next_attempt_at, occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_message (consumer_name VARCHAR(120) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (consumer_name,event_id));
CREATE TABLE seller_projection (seller_id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL, aggregate_version BIGINT NOT NULL, updated_at TIMESTAMPTZ NOT NULL);

INSERT INTO category(id, code, name, created_at) VALUES
 ('00000000-0000-0000-0000-000000000101','GENERAL','General',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000102','APPAREL','Apparel',CURRENT_TIMESTAMP),
 ('00000000-0000-0000-0000-000000000103','ELECTRONICS','Electronics',CURRENT_TIMESTAMP);
