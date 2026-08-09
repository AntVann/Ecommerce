INSERT INTO seller_role_permission(role_code, permission_code) VALUES
    ('OWNER', 'FULFILLMENT_WRITE'),
    ('MANAGER', 'FULFILLMENT_WRITE'),
    ('STAFF', 'FULFILLMENT_WRITE')
ON CONFLICT DO NOTHING;
