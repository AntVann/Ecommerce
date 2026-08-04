INSERT INTO seller_role_permission(role_code, permission_code) VALUES
    ('OWNER', 'CATALOG_WRITE'),
    ('OWNER', 'INVENTORY_WRITE'),
    ('MANAGER', 'CATALOG_WRITE'),
    ('MANAGER', 'INVENTORY_WRITE'),
    ('STAFF', 'CATALOG_WRITE')
ON CONFLICT DO NOTHING;
