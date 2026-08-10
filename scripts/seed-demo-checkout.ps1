[CmdletBinding()]
param(
    [switch]$SkipCustomer
)

$ErrorActionPreference = 'Stop'

# These identifiers are intentionally stable and local-only. They make the browser demo and
# Playwright checkout test repeatable without committing production data or credentials.
$sellerId = '11111111-1111-1111-1111-111111111111'
$sellerOwnerId = '22222222-2222-2222-2222-222222222222'
$productId = '33333333-3333-3333-3333-333333333333'
$variantId = '44444444-4444-4444-4444-444444444444'
$categoryId = '00000000-0000-0000-0000-000000000103'
$customerEmail = 'demo.customer@example.test'
$customerPassword = 'MarketFlowDemo!123'
$internalKey = 'local-development-only-change-me'

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

Write-Host 'Seeding an approved demo seller, published product, and available inventory...'
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
INSERT INTO product(id, seller_id, category_id, title, description, status, attributes, version, created_at, updated_at, published_at)
VALUES ('$productId', '$sellerId', '$categoryId', 'MarketFlow Demo Laptop', 'A seeded local fixture for the real storefront checkout path.', 'ACTIVE', '{}'::jsonb, 1, now(), now(), now())
ON CONFLICT (id) DO UPDATE SET seller_id = EXCLUDED.seller_id, category_id = EXCLUDED.category_id,
    title = EXCLUDED.title, description = EXCLUDED.description, status = 'ACTIVE', attributes = '{}'::jsonb,
    updated_at = now(), published_at = COALESCE(product.published_at, now());
INSERT INTO product_variant(id, product_id, seller_id, sku, canonical_sku, name, attributes, price_amount, price_currency, active, version, created_at, updated_at)
VALUES ('$variantId', '$productId', '$sellerId', 'DEMO-LAPTOP-16', 'DEMO-LAPTOP-16', '16 GB / 512 GB', '{}'::jsonb, 1299.99, 'USD', TRUE, 1, now(), now())
ON CONFLICT (id) DO UPDATE SET product_id = EXCLUDED.product_id, seller_id = EXCLUDED.seller_id,
    sku = EXCLUDED.sku, canonical_sku = EXCLUDED.canonical_sku, name = EXCLUDED.name,
    price_amount = EXCLUDED.price_amount, price_currency = EXCLUDED.price_currency, active = TRUE, updated_at = now();
"@ | Out-Null

Invoke-Psql -Service 'inventory-postgres' -User 'inventory_app' -Database 'marketflow_inventory' -Sql @"
INSERT INTO inventory_item(variant_id, seller_id, on_hand, reserved, version, created_at, updated_at)
VALUES ('$variantId', '$sellerId', 100, 0, 1, now(), now())
ON CONFLICT (variant_id) DO UPDATE SET seller_id = EXCLUDED.seller_id, on_hand = 100, reserved = 0,
    version = inventory_item.version + 1, updated_at = now();
"@ | Out-Null

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
Write-Host "Product URL: http://localhost:5173/products/$productId"
Write-Host "Product ID: $productId"
Write-Host "Variant ID: $variantId"
Write-Host "Seller ID: $sellerId"
if (-not $SkipCustomer) {
    Write-Host "Customer email: $customerEmail"
    Write-Host "Customer password: $customerPassword"
}
