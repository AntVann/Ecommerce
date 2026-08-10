[CmdletBinding()]
param([string]$OutputDirectory = '')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location -LiteralPath $root

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("marketflow-backup-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$databases = @(
    @{ Service = 'postgres'; Database = 'marketflow_foundation'; User = 'marketflow_local' },
    @{ Service = 'identity-postgres'; Database = 'marketflow_identity'; User = 'identity_app' },
    @{ Service = 'seller-postgres'; Database = 'marketflow_seller'; User = 'seller_app' },
    @{ Service = 'catalog-postgres'; Database = 'marketflow_catalog'; User = 'catalog_app' },
    @{ Service = 'inventory-postgres'; Database = 'marketflow_inventory'; User = 'inventory_app' },
    @{ Service = 'search-postgres'; Database = 'marketflow_search'; User = 'search_app' },
    @{ Service = 'order-postgres'; Database = 'marketflow_order'; User = 'order_app' },
    @{ Service = 'payment-postgres'; Database = 'marketflow_payment'; User = 'payment_app' },
    @{ Service = 'notification-postgres'; Database = 'marketflow_notification'; User = 'notification_app' }
)

$manifest = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $databases) {
    $file = Join-Path $OutputDirectory ($entry.Database + '.dump')
    Write-Output "Backing up $($entry.Database)"
    $dumpCommand = "docker compose exec -T $($entry.Service) pg_dump -Fc -U $($entry.User) -d $($entry.Database) > `"$file`""
    cmd.exe /d /s /c $dumpCommand
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed for $($entry.Database)." }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash
    $manifest.Add([pscustomobject]@{ database = $entry.Database; service = $entry.Service; file = $file; sha256 = $hash; createdAt = (Get-Date).ToUniversalTime().ToString('o') })
}

$manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $OutputDirectory 'manifest.json')
Write-Output "PASS PostgreSQL backups created in $OutputDirectory"
