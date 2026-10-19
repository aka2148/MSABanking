$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot '.env'

function New-HexSecret([int] $byteCount) {
    $bytes = [byte[]]::new($byteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function New-Base64Secret([int] $byteCount) {
    $bytes = [byte[]]::new($byteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

$existing = Test-Path -LiteralPath $envPath
$lines = if ($existing) { @(Get-Content -LiteralPath $envPath) } else { @() }
$keys = @{}
foreach ($line in $lines) { if ($line -match '^([^=]+)=') { $keys[$Matches[1]] = $true } }
$additions = @()

if (-not $existing) {
    $additions += 'MYSQL_APP_USER=moneypal_app'
    $additions += "MYSQL_APP_PASSWORD=$(New-HexSecret 24)"
    $additions += "MYSQL_ROOT_PASSWORD=$(New-HexSecret 24)"
    $additions += "JWT_SECRET=$(New-Base64Secret 64)"
    $additions += "MFA_ENCRYPTION_KEY=$(New-Base64Secret 64)"
}

if (-not $keys.ContainsKey('DEMO_ADMIN_USERNAME')) { $additions += 'DEMO_ADMIN_USERNAME=moneypal.admin' }
if (-not $keys.ContainsKey('DEMO_ADMIN_PASSWORD')) { $additions += "DEMO_ADMIN_PASSWORD=$(New-HexSecret 32)" }

if ($existing) {
    if ($additions.Count -gt 0) { Add-Content -LiteralPath $envPath -Value $additions -Encoding ascii }
    Write-Output 'Added any missing local demo admin credentials to ignored .env; existing values were preserved.'
} else {
    Set-Content -LiteralPath $envPath -Value $additions -Encoding ascii
    Write-Output 'Created ignored .env with random local-only database passwords, JWT key, and demo admin credentials.'
}
