[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repositoryRoot

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found on PATH."
    }
}

Assert-Command java
Assert-Command docker

$javaVersion = (& java --version | Select-Object -First 1) -join ''
if ($javaVersion -notmatch '\b21[.]') {
    throw "Java 21 is required. Detected: $javaVersion"
}

& docker compose version | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose is required.'
}

if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
    Write-Host 'Created .env from non-secret local development examples.'
} else {
    Write-Host 'Preserved existing .env.'
}

$mavenVersion = & .\mvnw.cmd --version
$mavenExitCode = $LASTEXITCODE
$mavenVersion | Select-Object -First 5 | Out-Host
if ($mavenExitCode -ne 0) {
    throw 'The Maven wrapper failed.'
}

Write-Host 'MarketFlow foundation prerequisites are ready.'
