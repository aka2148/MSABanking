[CmdletBinding()]
param(
    [string]$TrustedRootThumbprint = '979911A42735A4608DAD5B53A77E176AD05118D6'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$certDirectory = Join-Path $repoRoot '.planning\local-ca'
$certName = 'maven-root-' + [guid]::NewGuid().ToString('N') + '.cer'
$certificatePath = Join-Path $certDirectory $certName
$relativeCertificatePath = '.planning/local-ca/' + $certName
$overridePath = Join-Path $env:TEMP ('banking-compose-ca-' + [guid]::NewGuid().ToString('N') + '.yml')
$createdCertDirectory = $false

Push-Location $repoRoot
try {
    $trustedRoot = Get-ChildItem -Path Cert:\LocalMachine\Root |
        Where-Object Thumbprint -eq ($TrustedRootThumbprint -replace '\s', '') |
        Select-Object -First 1
    if (-not $trustedRoot) {
        throw "Trusted root certificate $TrustedRootThumbprint was not found in LocalMachine\Root."
    }

    if (-not (Test-Path -LiteralPath $certDirectory)) {
        New-Item -ItemType Directory -Path $certDirectory | Out-Null
        $createdCertDirectory = $true
    }
    Export-Certificate -Cert $trustedRoot -FilePath $certificatePath -Type CERT -Force | Out-Null

    $override = @"
services:
  identity-customer-service:
    build:
      secrets:
        - maven-root-ca
  product-service:
    build:
      secrets:
        - maven-root-ca
  fd-account-service:
    build:
      secrets:
        - maven-root-ca
secrets:
  maven-root-ca:
    file: ./$relativeCertificatePath
"@
    Set-Content -LiteralPath $overridePath -Value $override -Encoding utf8

    $composeArgs = @('compose', '--progress', 'plain', '-f', 'docker-compose.yml', '-f', $overridePath)
    foreach ($service in @('identity-customer-service', 'product-service', 'fd-account-service')) {
        Write-Host "Building $service with the locally trusted Maven CA..."
        & docker @composeArgs build $service
        if ($LASTEXITCODE -ne 0) {
            throw "Docker build failed for $service with exit code $LASTEXITCODE."
        }
    }

    & docker @composeArgs up -d --no-build identity-customer-service product-service fd-account-service
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose startup failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path -LiteralPath $certificatePath) {
        Remove-Item -LiteralPath $certificatePath -Force
    }
    if (Test-Path -LiteralPath $overridePath) {
        Remove-Item -LiteralPath $overridePath -Force
    }
    if ($createdCertDirectory -and (Test-Path -LiteralPath $certDirectory) -and
        -not (Get-ChildItem -LiteralPath $certDirectory -Force | Select-Object -First 1)) {
        Remove-Item -LiteralPath $certDirectory -Force
    }
    Pop-Location
}
