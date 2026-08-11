[CmdletBinding()]
param(
    [switch]$SkipCustomer
)

$ErrorActionPreference = 'Stop'

# These identifiers are intentionally stable and local-only. They make the browser demo and
# Playwright checkout test repeatable without committing production data or credentials.
$sellerId = '11111111-1111-1111-1111-111111111111'
$sellerOwnerId = '22222222-2222-2222-2222-222222222222'
$checkoutProductId = '33333333-3333-3333-3333-333333333333'
$checkoutVariantId = '44444444-4444-4444-4444-444444444444'
$legacyVariantId = '44444444-4444-4444-4444-444444444445'
$customerEmail = 'demo.customer@example.test'
$customerPassword = 'MarketFlowDemo!123'
$internalKey = 'local-development-only-change-me'

$products = @(
    [pscustomobject]@{ Id = $checkoutProductId; VariantId = $checkoutVariantId; CategoryId = '00000000-0000-0000-0000-000000000103'; Title = 'Aurora Pro Laptop'; Description = 'A lightweight laptop with a vivid display, quiet cooling, and all-day battery life.'; Sku = 'AURORA-LAPTOP-16'; VariantName = '16 GB / 512 GB'; Price = '1299.99'; Inventory = 100 },
    [pscustomobject]@{ Id = '55555555-5555-5555-5555-555555555555'; VariantId = '66666666-6666-6666-6666-666666666666'; CategoryId = '00000000-0000-0000-0000-000000000103'; Title = 'Nimbus Wireless Headphones'; Description = 'Comfortable over-ear headphones with rich sound, active noise reduction, and a long-lasting charge.'; Sku = 'NIMBUS-HEADPHONES-BLK'; VariantName = 'Midnight Black'; Price = '179.99'; Inventory = 60 },
    [pscustomobject]@{ Id = '77777777-7777-7777-7777-777777777777'; VariantId = '88888888-8888-8888-8888-888888888888'; CategoryId = '00000000-0000-0000-0000-000000000103'; Title = 'Terra Mechanical Keyboard'; Description = 'A compact mechanical keyboard with tactile switches, warm backlighting, and a sturdy aluminum frame.'; Sku = 'TERRA-KEYBOARD-TAC'; VariantName = 'Tactile Switches'; Price = '129.00'; Inventory = 45 },
    [pscustomobject]@{ Id = '99999999-9999-9999-9999-999999999999'; VariantId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'; CategoryId = '00000000-0000-0000-0000-000000000103'; Title = 'Solstice 4K Monitor'; Description = 'A crisp 27-inch monitor with accurate color, slim bezels, and adjustable viewing comfort.'; Sku = 'SOLSTICE-MONITOR-27'; VariantName = '27 inch 4K'; Price = '399.00'; Inventory = 30 },
    [pscustomobject]@{ Id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'; VariantId = 'cccccccc-cccc-cccc-cccc-cccccccccccc'; CategoryId = '00000000-0000-0000-0000-000000000102'; Title = 'Alpine Trail Jacket'; Description = 'A weather-resistant jacket with breathable insulation and practical pockets for changing conditions.'; Sku = 'ALPINE-JACKET-M'; VariantName = 'Forest Green / Medium'; Price = '149.00'; Inventory = 35 },
    [pscustomobject]@{ Id = 'dddddddd-dddd-dddd-dddd-dddddddddddd'; VariantId = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'; CategoryId = '00000000-0000-0000-0000-000000000102'; Title = 'Harbor Canvas Sneakers'; Description = 'Everyday canvas sneakers with a cushioned footbed and flexible rubber sole.'; Sku = 'HARBOR-SNEAKERS-9'; VariantName = 'Stone / Size 9'; Price = '89.00'; Inventory = 50 },
    [pscustomobject]@{ Id = 'ffffffff-ffff-ffff-ffff-ffffffffffff'; VariantId = '12121212-1212-1212-1212-121212121212'; CategoryId = '00000000-0000-0000-0000-000000000101'; Title = 'Cedar Desk Lamp'; Description = 'A warm adjustable desk lamp with a natural wood accent and USB charging port.'; Sku = 'CEDAR-LAMP-WAL'; VariantName = 'Walnut'; Price = '59.00'; Inventory = 40 },
    [pscustomobject]@{ Id = '13131313-1313-1313-1313-131313131313'; VariantId = '14141414-1414-1414-1414-141414141414'; CategoryId = '00000000-0000-0000-0000-000000000101'; Title = 'Orbit Travel Backpack'; Description = 'A durable carry-on backpack with a padded laptop sleeve, organizer pockets, and a comfortable harness.'; Sku = 'ORBIT-BACKPACK-22'; VariantName = '22 Liter / Slate'; Price = '74.00'; Inventory = 55 }
)

function Invoke-Psql {
    param(
        [Parameter(Mandatory)] [string]$Service,
        [Parameter(Mandatory)] [string]$User,
        [Parameter(Mandatory)] [string]$Database,
        [Parameter(Mandatory)] [string]$Sql
    )

    $output = docker compose exec -T $Service psql -v ON_ERROR_STOP=1 -At -U $User -d $Database -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL seed failed for $Service."
    }
    return (($output -join "`n").Trim())
}

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory)] [string]$Uri,
        [Parameter(Mandatory)] [object]$Body,
        [hashtable]$Headers = @{}
    )

    return Invoke-RestMethod -Method Post -Uri $Uri -ContentType 'application/json' `
        -Headers $Headers -Body ($Body | ConvertTo-Json -Compress)
}

Write-Host 'Starting local MarketFlow services required by the checkout fixture...'
docker compose up -d --wait identity-service seller-service catalog-service inventory-service search-service | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw 'Compose services did not become ready.'
}

Write-Host 'Seeding an approved seller, published products, and available inventory...'
Invoke-Psql -Service 'seller-postgres' -User 'seller_app' -Database 'marketflow_seller' -Sql @"
INSERT INTO seller(id, applicant_user_id, display_name, legal_name, country_code, status, created_at, updated_at, version)
VALUES ('$sellerId', '$sellerOwnerId', 'MarketFlow Demo Store', 'MarketFlow Demo LLC', 'US', 'APPROVED', now(), now(), 1)
ON CONFLICT (id) DO UPDATE SET status = 'APPROVED', display_name = EXCLUDED.display_name,
    legal_name = EXCLUDED.legal_name, updated_at = now();
"@ | Out-Null

Invoke-Psql -Service 'catalog-postgres' -User 'catalog_app' -Database 'marketflow_catalog' -Sql @"
INSERT INTO seller_projection(seller_id, status, aggregate_version, updated_at)
VALUES ('$sellerId', 'APPROVED', 1, now())
ON CONFLICT (seller_id) DO UPDATE SET status = 'APPROVED', aggregate_version = GREATEST(seller_projection.aggregate_version, 1), updated_at = now();
DELETE FROM product_image
WHERE product_id IN (SELECT id FROM product WHERE seller_id = '$sellerId')
  AND (object_key ILIKE '%demo%' OR object_key ILIKE '%marketflow%' OR alt_text ILIKE '%demo%' OR alt_text ILIKE '%marketflow%');
UPDATE product_variant
SET product_id = '$checkoutProductId', seller_id = '$sellerId', sku = 'AURORA-LAPTOP-8',
    canonical_sku = 'AURORA-LAPTOP-8', name = '8 GB / 256 GB', attributes = '{}'::jsonb,
    price_amount = 899.99, price_currency = 'USD', active = TRUE, updated_at = now()
WHERE id = '$legacyVariantId';
"@ | Out-Null

Invoke-Psql -Service 'inventory-postgres' -User 'inventory_app' -Database 'marketflow_inventory' -Sql @"
DELETE FROM inventory_reservation
WHERE id IN (
    SELECT reservation_id FROM inventory_reservation_line WHERE variant_id = '$legacyVariantId'
);
INSERT INTO inventory_item(variant_id, seller_id, on_hand, reserved, version, created_at, updated_at)
VALUES ('$legacyVariantId', '$sellerId', 25, 0, 1, now(), now())
ON CONFLICT (variant_id) DO UPDATE SET seller_id = EXCLUDED.seller_id, on_hand = EXCLUDED.on_hand, reserved = 0,
    version = inventory_item.version + 1, updated_at = now();
"@ | Out-Null

foreach ($product in $products) {
    Invoke-Psql -Service 'catalog-postgres' -User 'catalog_app' -Database 'marketflow_catalog' -Sql @"
INSERT INTO product(id, seller_id, category_id, title, description, status, attributes, version, created_at, updated_at, published_at)
VALUES ('$($product.Id)', '$sellerId', '$($product.CategoryId)', '$($product.Title)', '$($product.Description)', 'ACTIVE', '{}'::jsonb, 1, now(), now(), now())
ON CONFLICT (id) DO UPDATE SET seller_id = EXCLUDED.seller_id, category_id = EXCLUDED.category_id,
    title = EXCLUDED.title, description = EXCLUDED.description, status = 'ACTIVE', attributes = '{}'::jsonb,
    updated_at = now(), published_at = COALESCE(product.published_at, now());
INSERT INTO product_variant(id, product_id, seller_id, sku, canonical_sku, name, attributes, price_amount, price_currency, active, version, created_at, updated_at)
VALUES ('$($product.VariantId)', '$($product.Id)', '$sellerId', '$($product.Sku)', '$($product.Sku)', '$($product.VariantName)', '{}'::jsonb, $($product.Price), 'USD', TRUE, 1, now(), now())
ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id, seller_id = EXCLUDED.seller_id,
    sku = EXCLUDED.sku, canonical_sku = EXCLUDED.canonical_sku, name = EXCLUDED.name,
    price_amount = EXCLUDED.price_amount, price_currency = EXCLUDED.price_currency, active = TRUE, updated_at = now();
"@ | Out-Null

    Invoke-Psql -Service 'inventory-postgres' -User 'inventory_app' -Database 'marketflow_inventory' -Sql @"
DELETE FROM inventory_reservation
WHERE id IN (
    SELECT reservation_id FROM inventory_reservation_line WHERE variant_id = '$($product.VariantId)'
);
INSERT INTO inventory_item(variant_id, seller_id, on_hand, reserved, version, created_at, updated_at)
VALUES ('$($product.VariantId)', '$sellerId', $($product.Inventory), 0, 1, now(), now())
ON CONFLICT (variant_id) DO UPDATE SET seller_id = EXCLUDED.seller_id, on_hand = EXCLUDED.on_hand, reserved = 0,
    version = inventory_item.version + 1, updated_at = now();
"@ | Out-Null
}

Write-Host 'Rebuilding the local OpenSearch projection from the catalog fixture...'
$rebuild = Invoke-RestMethod -Method Post -Uri 'http://localhost:8085/internal/v1/search/rebuild' `
    -Headers @{ 'X-Internal-Service-Key' = $internalKey }
if ($rebuild.failed -ne 0) {
    throw "Search rebuild reported $($rebuild.failed) failed document(s)."
}

if (-not $SkipCustomer) {
    Write-Host "Seeding and verifying local customer $customerEmail..."
    $status = Invoke-Psql -Service 'identity-postgres' -User 'identity_app' -Database 'marketflow_identity' -Sql @"
SELECT status FROM user_account WHERE normalized_email = '$customerEmail';
"@
    if ([string]::IsNullOrWhiteSpace($status)) {
        Invoke-JsonPost -Uri 'http://localhost:8081/api/v1/auth/register' `
            -Body @{ email = $customerEmail; password = $customerPassword } | Out-Null
        $status = Invoke-Psql -Service 'identity-postgres' -User 'identity_app' -Database 'marketflow_identity' -Sql @"
SELECT status FROM user_account WHERE normalized_email = '$customerEmail';
"@
    }
    if ($status -eq 'PENDING_VERIFICATION') {
        Invoke-JsonPost -Uri 'http://localhost:8081/api/v1/auth/email-verifications/resend' `
            -Body @{ email = $customerEmail } | Out-Null
        $verificationId = Invoke-Psql -Service 'identity-postgres' -User 'identity_app' -Database 'marketflow_identity' -Sql @"
SELECT ev.id FROM email_verification ev
JOIN user_account ua ON ua.id = ev.user_id
WHERE ua.normalized_email = '$customerEmail' AND ev.status = 'QUEUED'
ORDER BY ev.created_at DESC LIMIT 1;
"@
        $delivery = Invoke-RestMethod -Method Post `
            -Uri "http://localhost:8081/internal/v1/email-verifications/$verificationId/token" `
            -Headers @{ 'X-Internal-Service-Key' = $internalKey }
        Invoke-JsonPost -Uri 'http://localhost:8081/api/v1/auth/email-verifications/confirm' `
            -Body @{ verificationId = $verificationId; token = $delivery.token } | Out-Null
    }
}

Write-Host ''
Write-Host 'Demo checkout fixture is ready.'
Write-Host "Product URL: http://localhost:5173/products/$checkoutProductId"
Write-Host "Product ID: $checkoutProductId"
Write-Host "Variant ID: $checkoutVariantId"
Write-Host "Catalog items seeded: $($products.Count)"
Write-Host "Seller ID: $sellerId"
if (-not $SkipCustomer) {
    Write-Host "Customer email: $customerEmail"
    Write-Host "Customer password: $customerPassword"
}
