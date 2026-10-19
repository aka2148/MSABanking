param(
    [string] $HostName = '127.0.0.1',
    [int] $Port = 3306,
    [string] $AdminUsername = 'root'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Run scripts/initialize-demo-env.ps1 first so the local demo .env exists.'
}

$mysqlExe = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysqlExe) {
    $candidate = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    if (Test-Path -LiteralPath $candidate) { $mysqlExe = $candidate }
}
if (-not $mysqlExe) { throw 'MySQL client not found. Install MySQL Server/Client or add mysql.exe to PATH.' }

function New-DbPassword {
    $bytes = [byte[]]::new(32)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Set-EnvValue([string[]] $lines, [string] $key, [string] $value) {
    $prefix = "$key="
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line.StartsWith($prefix, [StringComparison]::Ordinal)) {
            $found = $true
            "$prefix$value"
        } else { $line }
    }
    if (-not $found) { $updated += "$prefix$value" }
    return ,$updated
}

$lines = @(Get-Content -LiteralPath $envPath)
$passwords = @{
    CUSTOMER = New-DbPassword
    PRODUCT = New-DbPassword
    FD = New-DbPassword
}
$databases = @(
    @{ Service = 'CUSTOMER'; Database = 'moneypal_customer'; User = 'moneypal_identity_app' },
    @{ Service = 'PRODUCT'; Database = 'moneypal_product'; User = 'moneypal_product_app' },
    @{ Service = 'FD'; Database = 'moneypal_fd'; User = 'moneypal_fd_app' }
)

$sql = [System.Text.StringBuilder]::new()
foreach ($entry in $databases) {
    $password = $passwords[$entry.Service]
    [void] $sql.AppendLine("CREATE DATABASE IF NOT EXISTS ``$($entry.Database)``;")
    [void] $sql.AppendLine("CREATE USER IF NOT EXISTS '$($entry.User)'@'%' IDENTIFIED BY '$password';")
    [void] $sql.AppendLine("ALTER USER '$($entry.User)'@'%' IDENTIFIED BY '$password';")
    [void] $sql.AppendLine("GRANT ALL PRIVILEGES ON ``$($entry.Database)``.* TO '$($entry.User)'@'%';")
    $lines = Set-EnvValue $lines "$($entry.Service)_DB_URL" "jdbc:mysql://host.docker.internal:3306/$($entry.Database)?serverTimezone=UTC"
    $lines = Set-EnvValue $lines "$($entry.Service)_DB_USERNAME" $entry.User
    $lines = Set-EnvValue $lines "$($entry.Service)_DB_PASSWORD" $password
    $lines = Set-EnvValue $lines "$($entry.Service)_FLYWAY_BASELINE" 'true'
}

$securePassword = Read-Host "MySQL admin password for $AdminUsername@$HostName`:$Port" -AsSecureString
$adminPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $mysqlExe
$startInfo.Arguments = "--protocol=tcp --host=$HostName --port=$Port --user=$AdminUsername --batch --show-warnings"
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.EnvironmentVariables['MYSQL_PWD'] = $adminPassword
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
try {
    [void] $process.Start()
    $process.StandardInput.Write($sql.ToString())
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "MySQL setup failed. No .env changes were saved. $($stderr.Trim())"
    }

    $tmpPath = "$envPath.tmp"
    Set-Content -LiteralPath $tmpPath -Value $lines -Encoding ascii
    Move-Item -LiteralPath $tmpPath -Destination $envPath -Force
    Write-Output 'Created the three service databases if missing and granted each service access only to its own database.'
    Write-Output 'Saved the Workbench MySQL URLs and unique service credentials to ignored .env.'
    Write-Output 'Now restart with: docker compose up --build -d'
} finally {
    $adminPassword = $null
    $securePassword = $null
    $sql.Clear() | Out-Null
    $process.Dispose()
}
