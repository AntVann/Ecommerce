[CmdletBinding()]
param([int]$TimeoutSeconds = 120)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

function Wait-Ready([string]$Name, [string]$Url) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            $content = if ($response.Content -is [byte[]]) {
                [Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                [string]$response.Content
            }
            if ($response.StatusCode -eq 200 -and $content -match '"status":"UP"') {
                Write-Output "PASS recovery $Name"
                return
            }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Name readiness."
}

function Restart-And-Verify([string]$Dependency, [string]$Name, [string]$Url) {
    Write-Output "DRILL stop $Dependency"
    docker compose stop $Dependency | Out-Host
    Start-Sleep -Seconds 4
    Write-Output "DRILL restore $Dependency"
    docker compose start $Dependency | Out-Host
    Wait-Ready $Name $Url
}

docker compose ps | Out-Host
Restart-And-Verify 'redis' 'cart-service after Redis recovery' 'http://localhost:8086/actuator/health/readiness'
Restart-And-Verify 'kafka' 'order-service after Kafka recovery' 'http://localhost:8087/actuator/health/readiness'
Restart-And-Verify 'rabbitmq' 'notification-service after RabbitMQ recovery' 'http://localhost:8089/actuator/health/readiness'
Restart-And-Verify 'identity-postgres' 'identity-service after database recovery' 'http://localhost:8081/actuator/health/readiness'
Restart-And-Verify 'notification-service' 'notification-service process recovery' 'http://localhost:8089/actuator/health/readiness'

Write-Output 'PASS local dependency recovery drills. No volumes or databases were deleted.'
