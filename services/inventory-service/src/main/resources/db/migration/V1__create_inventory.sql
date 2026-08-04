CREATE TABLE inventory_item (
    variant_id UUID PRIMARY KEY, seller_id UUID NOT NULL,
    on_hand INTEGER NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= on_hand),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_inventory_seller ON inventory_item(seller_id, variant_id);

CREATE TABLE stock_movement (
    id UUID PRIMARY KEY, variant_id UUID NOT NULL REFERENCES inventory_item(variant_id), seller_id UUID NOT NULL,
    movement_type VARCHAR(24) NOT NULL CHECK (movement_type IN ('ADJUSTMENT','RESERVATION','RELEASE','EXPIRATION')),
    quantity_delta INTEGER NOT NULL, reason_code VARCHAR(80) NOT NULL, reference_id UUID,
    actor_user_id UUID, correlation_id VARCHAR(128) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_stock_movement_variant ON stock_movement(variant_id, occurred_at DESC);

CREATE TABLE inventory_reservation (
    id UUID PRIMARY KEY, reference_id UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','RELEASED','EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE inventory_reservation_line (
    reservation_id UUID NOT NULL REFERENCES inventory_reservation(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES inventory_item(variant_id), quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, variant_id)
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY, event_type VARCHAR(120) NOT NULL, aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL, aggregate_version BIGINT NOT NULL, correlation_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_inventory_outbox_pending ON outbox_event(next_attempt_at, occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_message (consumer_name VARCHAR(120) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (consumer_name,event_id));
CREATE TABLE idempotency_record (operation VARCHAR(120) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, request_hash CHAR(64) NOT NULL, resource_id UUID, resource_version BIGINT, response_payload JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(operation,idempotency_key));
