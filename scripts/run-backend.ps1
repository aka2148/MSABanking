$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and fill in the local values."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([^#=\s]+)\s*=(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

# The legacy Spring Boot backend uses generic datasource variable names.
$env:DB_URL = $env:SUPABASE_DB_URL
$env:DB_USERNAME = $env:SUPABASE_DB_USERNAME
$env:DB_PASSWORD = $env:SUPABASE_DB_PASSWORD

Push-Location (Join-Path $repoRoot 'BACKEND')
try {
    & mvn spring-boot:run
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
