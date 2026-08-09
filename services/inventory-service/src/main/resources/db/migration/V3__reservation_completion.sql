ALTER TABLE inventory_reservation DROP CONSTRAINT inventory_reservation_status_check;
ALTER TABLE inventory_reservation
    ADD CONSTRAINT inventory_reservation_status_check
    CHECK (status IN ('ACTIVE','PENDING','CONFIRMED','RELEASED','EXPIRED'));

ALTER TABLE stock_movement DROP CONSTRAINT stock_movement_movement_type_check;
ALTER TABLE stock_movement
    ADD CONSTRAINT stock_movement_movement_type_check
    CHECK (movement_type IN ('ADJUSTMENT','RESERVATION','COMMITMENT','RELEASE','EXPIRATION'));
