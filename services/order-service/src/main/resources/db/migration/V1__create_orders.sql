CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    cart_id UUID NOT NULL,
    cart_version BIGINT NOT NULL CHECK (cart_version >= 1),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','INVENTORY_RESERVED','CANCELLED','MANUAL_REVIEW')),
    cancellation_reason VARCHAR(80),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    shipping_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (shipping_total >= 0),
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    grand_total NUMERIC(19,4) NOT NULL CHECK (grand_total >= 0),
    shipping_address JSONB NOT NULL,
    billing_address JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(customer_id, cart_id, cart_version)
);
CREATE INDEX ix_order_customer_created ON customer_order(customer_id, created_at DESC);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    variant_name VARCHAR(160) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    line_subtotal NUMERIC(19,4) NOT NULL CHECK (line_subtotal >= 0),
    catalog_version BIGINT NOT NULL CHECK (catalog_version >= 1),
    UNIQUE(order_id, variant_id)
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES customer_order(id),
    previous_status VARCHAR(32), new_status VARCHAR(32) NOT NULL,
    reason VARCHAR(80), correlation_id VARCHAR(128) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_order_history ON order_status_history(order_id, occurred_at);

CREATE TABLE order_inventory_progress (
    order_id UUID NOT NULL REFERENCES customer_order(id), variant_id UUID NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('RESERVED','RELEASED','FAILED')),
    event_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(order_id, variant_id)
);

CREATE TABLE order_saga (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id),
    expected_lines INTEGER NOT NULL CHECK (expected_lines > 0),
    reserved_lines INTEGER NOT NULL DEFAULT 0 CHECK (reserved_lines >= 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('AWAITING_INVENTORY','INVENTORY_RESERVED','CANCELLED','MANUAL_REVIEW')),
    deadline_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_order_saga_stale ON order_saga(deadline_at) WHERE state='AWAITING_INVENTORY';

CREATE TABLE idempotency_record (
    customer_id UUID NOT NULL, operation VARCHAR(80) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL, order_id UUID REFERENCES customer_order(id),
    http_status INTEGER, response_payload JSONB, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(customer_id, operation, idempotency_key)
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY, event_type VARCHAR(120) NOT NULL, aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL, aggregate_version BIGINT NOT NULL, correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_order_outbox_pending ON outbox_event(next_attempt_at, occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(consumer_name,event_id)
);
