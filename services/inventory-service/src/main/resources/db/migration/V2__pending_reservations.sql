ALTER TABLE inventory_reservation DROP CONSTRAINT inventory_reservation_status_check;
UPDATE inventory_reservation SET status = 'PENDING' WHERE status = 'ACTIVE';
ALTER TABLE inventory_reservation
    ADD CONSTRAINT inventory_reservation_status_check
    CHECK (status IN ('ACTIVE','PENDING','RELEASED','EXPIRED'));
CREATE INDEX ix_inventory_reservation_expiry
    ON inventory_reservation(expires_at, id)
    WHERE status IN ('ACTIVE','PENDING');
