ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('PENDING','INVENTORY_RESERVED','PAYMENT_PROCESSING','CONFIRMED','PAYMENT_FAILED','CANCELLED','MANUAL_REVIEW'));
ALTER TABLE customer_order
    ADD COLUMN payment_id UUID,
    ADD COLUMN payment_state VARCHAR(32),
    ADD COLUMN payment_updated_at TIMESTAMPTZ;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_payment_state_check
    CHECK (payment_state IS NULL OR payment_state IN ('PROCESSING','AUTHORIZED','DECLINED','FAILED','UNKNOWN'));

ALTER TABLE order_saga DROP CONSTRAINT order_saga_state_check;
ALTER TABLE order_saga ADD CONSTRAINT order_saga_state_check
    CHECK (state IN ('AWAITING_INVENTORY','INVENTORY_RESERVED','PAYMENT_PROCESSING','PAYMENT_UNKNOWN','CONFIRMED','PAYMENT_FAILED','CANCELLED','MANUAL_REVIEW'));

CREATE TABLE payment_initiation (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id),
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(customer_id, idempotency_key)
);

CREATE INDEX ix_order_seller_created ON order_item(seller_id, order_id);
CREATE INDEX ix_order_payment_unknown ON order_saga(deadline_at, order_id)
    WHERE state='PAYMENT_UNKNOWN';
