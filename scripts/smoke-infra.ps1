[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repositoryRoot

function Wait-Http {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Uri,
        [hashtable]$Headers = @{},
        [int]$Attempts = 30
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Headers $Headers -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-Host "PASS $Name ($Uri)"
                return $response
            }
        } catch {
            if ($attempt -eq $Attempts) { throw }
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become ready: $Uri"
}

function Get-ResponseContent {
    param([Parameter(Mandatory)]$Response)

    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

$smokeCorrelationId = 'm1-infrastructure-smoke'
$readiness = Wait-Http -Name 'sample readiness' `
    -Uri 'http://localhost:8080/actuator/health/readiness' `
    -Headers @{ 'X-Correlation-ID' = $smokeCorrelationId }
if ((Get-ResponseContent -Response $readiness) -notmatch '"status"\s*:\s*"UP"') {
    throw 'Sample-service readiness did not report UP.'
}
if ($readiness.Headers['X-Correlation-ID'] -ne $smokeCorrelationId) {
    throw 'Sample-service did not echo the correlation ID.'
}

$identityReadiness = Wait-Http -Name 'identity readiness' `
    -Uri 'http://localhost:8081/actuator/health/readiness' `
    -Headers @{ 'X-Correlation-ID' = $smokeCorrelationId }
if ((Get-ResponseContent -Response $identityReadiness) -notmatch '"status"\s*:\s*"UP"') {
    throw 'Identity-service readiness did not report UP.'
}
$sellerReadiness = Wait-Http -Name 'seller readiness' `
    -Uri 'http://localhost:8082/actuator/health/readiness' `
    -Headers @{ 'X-Correlation-ID' = $smokeCorrelationId }
if ((Get-ResponseContent -Response $sellerReadiness) -notmatch '"status"\s*:\s*"UP"') {
    throw 'Seller-service readiness did not report UP.'
}

$metrics = Wait-Http -Name 'sample metrics' -Uri 'http://localhost:8080/actuator/prometheus'
if ((Get-ResponseContent -Response $metrics) -notmatch 'jvm_info') {
    throw 'Expected JVM metrics were not published.'
}
$identityMetrics = Wait-Http -Name 'identity metrics' -Uri 'http://localhost:8081/actuator/prometheus'
if ((Get-ResponseContent -Response $identityMetrics) -notmatch 'authentication_failure_total') {
    throw 'Expected Identity security metrics were not published.'
}
$sellerMetrics = Wait-Http -Name 'seller metrics' -Uri 'http://localhost:8082/actuator/prometheus'
if ((Get-ResponseContent -Response $sellerMetrics) -notmatch 'authorization_denied_total') {
    throw 'Expected Seller authorization metrics were not published.'
}

$targets = Wait-Http -Name 'Prometheus API' -Uri 'http://localhost:9090/api/v1/targets'
$targetsJson = Get-ResponseContent -Response $targets | ConvertFrom-Json
$sampleTarget = $targetsJson.data.activeTargets | Where-Object { $_.labels.job -eq 'sample-service' }
if ($null -eq $sampleTarget -or $sampleTarget.health -ne 'up') {
    throw 'Prometheus is not scraping the sample service successfully.'
}
foreach ($job in @('identity-service', 'seller-service')) {
    $target = $targetsJson.data.activeTargets | Where-Object { $_.labels.job -eq $job }
    if ($null -eq $target -or $target.health -ne 'up') {
        throw "Prometheus is not scraping $job successfully."
    }
}

Wait-Http -Name 'Grafana' -Uri 'http://localhost:3000/api/health' | Out-Null
Wait-Http -Name 'Tempo' -Uri 'http://localhost:3200/ready' | Out-Null
Wait-Http -Name 'OpenSearch' -Uri 'http://localhost:9200/_cluster/health' | Out-Null
Wait-Http -Name 'SeaweedFS master' -Uri 'http://localhost:9333/cluster/status' | Out-Null

& docker compose exec -T postgres pg_isready -U marketflow_local -d marketflow_foundation
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL readiness check failed.' }
& docker compose exec -T identity-postgres pg_isready -U identity_app -d marketflow_identity
if ($LASTEXITCODE -ne 0) { throw 'Identity PostgreSQL readiness check failed.' }
& docker compose exec -T seller-postgres pg_isready -U seller_app -d marketflow_seller
if ($LASTEXITCODE -ne 0) { throw 'Seller PostgreSQL readiness check failed.' }
$kafkaTopics = (& docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0 -or $kafkaTopics -notmatch 'marketflow.identity.events.v1' -or $kafkaTopics -notmatch 'marketflow.seller.events.v1') {
    throw 'Milestone 1 Kafka topics were not provisioned.'
}
& docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping
if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ readiness check failed.' }
$redisResult = (& docker compose exec -T redis redis-cli ping) -join ''
if ($LASTEXITCODE -ne 0 -or $redisResult.Trim() -ne 'PONG') { throw 'Redis readiness check failed.' }

Start-Sleep -Seconds 3
$traces = Wait-Http -Name 'Tempo trace search' -Uri 'http://localhost:3200/api/search?limit=20'
$traceJson = Get-ResponseContent -Response $traces | ConvertFrom-Json
if ($null -eq $traceJson.traces -or $traceJson.traces.Count -lt 1) {
    throw 'No sample-service trace reached Tempo.'
}

$serviceLogs = (& docker compose logs --no-color --tail 300 sample-service identity-service seller-service) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0 -or $serviceLogs -notmatch '"correlationId":"m1-infrastructure-smoke"') {
    throw 'Structured service logs did not contain the smoke correlation ID.'
}
Write-Host 'PASS structured correlation log'

Write-Host 'All MarketFlow Milestone 1 infrastructure smoke checks passed.'
