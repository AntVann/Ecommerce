[CmdletBinding()]
param([Parameter(Mandatory)][string]$BackupDirectory)

$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $BackupDirectory 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath)) { throw 'manifest.json was not found.' }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json

foreach ($entry in $manifest) {
    $container = "marketflow-restore-$([Guid]::NewGuid().ToString('N').Substring(0, 12))"
    $file = [string]$entry.file
    try {
        docker run --detach --name $container -e POSTGRES_PASSWORD=restore-only postgres:17-alpine | Out-Null
        $ready = $false
        for ($i = 0; $i -lt 30; $i++) {
            docker exec $container pg_isready -U postgres -h 127.0.0.1 | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw "Temporary restore database did not become ready for $($entry.database)." }
        docker exec $container createdb -U postgres -h 127.0.0.1 $entry.database
        if ($LASTEXITCODE -ne 0) { throw "Could not create restore database $($entry.database)." }
        $restoreCommand = "docker exec -i $container pg_restore -U postgres -h 127.0.0.1 -d $($entry.database) --no-owner --no-acl --exit-on-error < `"$file`""
        cmd.exe /d /s /c $restoreCommand
        if ($LASTEXITCODE -ne 0) { throw "pg_restore failed for $($entry.database)." }
        Write-Output "PASS restore $($entry.database)"
    } finally {
        docker rm --force $container | Out-Null
    }
}
Write-Output 'PASS all PostgreSQL backups restored into disposable databases.'
