ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('PENDING','INVENTORY_RESERVED','PAYMENT_PROCESSING','CONFIRMED','FULFILLING','SHIPPED','PAYMENT_FAILED','CANCELLED','MANUAL_REVIEW'));
ALTER TABLE order_saga DROP CONSTRAINT order_saga_state_check;
ALTER TABLE order_saga ADD CONSTRAINT order_saga_state_check
    CHECK (state IN ('AWAITING_INVENTORY','INVENTORY_RESERVED','PAYMENT_PROCESSING','PAYMENT_UNKNOWN','CONFIRMED','FULFILLING','SHIPPED','PAYMENT_FAILED','CANCELLED','MANUAL_REVIEW'));

ALTER TABLE order_item ADD COLUMN fulfilled_quantity INTEGER NOT NULL DEFAULT 0 CHECK (fulfilled_quantity >= 0 AND fulfilled_quantity <= quantity);

CREATE TABLE shipment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('CREATED','IN_TRANSIT','DELIVERED')),
    carrier VARCHAR(80) NOT NULL,
    tracking_number VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (seller_id, carrier, tracking_number)
);
CREATE INDEX ix_shipment_order ON shipment(order_id, created_at);
CREATE INDEX ix_shipment_seller ON shipment(seller_id, created_at DESC);

CREATE TABLE shipment_line (
    shipment_id UUID NOT NULL REFERENCES shipment(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_item(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    PRIMARY KEY (shipment_id, order_item_id)
);

CREATE TABLE shipment_status_history (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment(id) ON DELETE CASCADE,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_shipment_history ON shipment_status_history(shipment_id, occurred_at);

CREATE TABLE shipment_idempotency (
    seller_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    shipment_id UUID REFERENCES shipment(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (seller_id, idempotency_key)
);
