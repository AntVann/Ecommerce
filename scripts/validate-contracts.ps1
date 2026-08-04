[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$contractsPath = Join-Path $repositoryRoot 'contracts'
$nodeImage = 'node:22.20.0-alpine'

function Invoke-LocalValidation {
    Push-Location -LiteralPath $contractsPath
    try {
        & npm ci --ignore-scripts
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
        & npm run lint
        if ($LASTEXITCODE -ne 0) { throw 'Contract validation failed.' }
    } finally {
        Pop-Location
    }
}

$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($null -ne $nodeCommand) {
    $majorVersion = [int]((& node --version).TrimStart('v').Split('.')[0])
    if ($majorVersion -ge 20) {
        Invoke-LocalValidation
        exit 0
    }
}

if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Contract validation requires Node.js 20+ or Docker.'
}

& docker run --rm --volume "${contractsPath}:/contracts" --workdir /contracts $nodeImage `
    sh -c 'npm ci --ignore-scripts && npm run lint'
if ($LASTEXITCODE -ne 0) {
    throw 'Containerized contract validation failed.'
}

