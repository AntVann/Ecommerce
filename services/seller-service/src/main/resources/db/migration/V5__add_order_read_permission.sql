INSERT INTO seller_role_permission(role_code, permission_code) VALUES
    ('OWNER', 'ORDER_READ'),
    ('MANAGER', 'ORDER_READ'),
    ('STAFF', 'ORDER_READ')
ON CONFLICT DO NOTHING;
