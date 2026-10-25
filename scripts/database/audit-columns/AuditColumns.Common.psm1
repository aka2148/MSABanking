Set-StrictMode -Version Latest

$script:AuditSchemaIds = [ordered]@{
    moneypal_fd       = 1
    moneypal_customer = 2
    moneypal_product  = 3
}
$script:AuditManifestSchema = 'moneypal_fd'
$script:AuditManifestTable = '_codex_audit_columns_manifest'
$script:AuditRunId = 'banking_audit_columns_v1'

function ConvertTo-AuditSqlIdentifier {
    param([Parameter(Mandatory)][string]$Value)
    return '`' + $Value.Replace('`', '``') + '`'
}

function ConvertTo-AuditSqlLiteral {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-AuditTriggerName {
    param(
        [Parameter(Mandatory)][string]$Schema,
        [Parameter(Mandatory)][string]$Table,
        [Parameter(Mandatory)][ValidateSet('bi', 'bu')][string]$Suffix
    )
    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("$Schema.$Table")
        $hex = ([System.BitConverter]::ToString($md5.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $md5.Dispose()
    }
    return 'trg_audit_' + $hex.Substring(0, 24) + '_' + $Suffix
}

function Get-AuditMysqlArguments {
    param(
        [string]$LoginPath,
        [string]$UserName,
        [string]$HostName,
        [int]$Port = 0,
        [switch]$Batch
    )
    $arguments = [System.Collections.Generic.List[string]]::new()
    if ($LoginPath) { $arguments.Add("--login-path=$LoginPath") }
    if ($UserName) { $arguments.Add("--user=$UserName") }
    if (-not $LoginPath -and -not $UserName) {
        throw 'Specify either a MySQL login path or a username.'
    }
    $arguments.Add('--default-character-set=utf8mb4')
    $arguments.Add('--database=moneypal_fd')
    if ($HostName) { $arguments.Add("--host=$HostName") }
    if ($Port -gt 0) { $arguments.Add("--port=$Port") }
    if ($Batch) {
        $arguments.Add('--batch')
        $arguments.Add('--skip-column-names')
    }
    return $arguments.ToArray()
}

function Get-AuditMysqlClientPath {
    $client = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if (-not $client) { $client = Get-Command mysql -ErrorAction SilentlyContinue }
    if ($client) { return $client.Source }

    foreach ($version in @('8.4', '8.0')) {
        $candidate = Join-Path $env:ProgramFiles "MySQL\MySQL Server $version\bin\mysql.exe"
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    throw 'mysql.exe was not found on PATH or under Program Files\MySQL.'
}

function Invoke-AuditMysqlQuery {
    param(
        [string]$LoginPath,
        [string]$UserName,
        [Parameter(Mandatory)][string]$Query,
        [string]$HostName,
        [int]$Port = 0
    )
    $mysql = Get-AuditMysqlClientPath
    $arguments = @(Get-AuditMysqlArguments -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port -Batch)
    $output = & $mysql @arguments --execute $Query 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "MySQL metadata query failed (exit $exitCode): $($output -join [Environment]::NewLine)"
    }
    return @($output | ForEach-Object { [string]$_ })
}

function Invoke-AuditMysqlFile {
    param(
        [string]$LoginPath,
        [string]$UserName,
        [Parameter(Mandatory)][string]$Path,
        [string]$HostName,
        [int]$Port = 0
    )
    $mysql = Get-AuditMysqlClientPath
    $arguments = @(Get-AuditMysqlArguments -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port)
    $quoteArg = { param([string]$value) '"' + $value.Replace('"', '\"') + '"' }
    $argumentText = (($arguments | ForEach-Object { & $quoteArg $_ }) -join ' ')

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysql
    $startInfo.Arguments = $argumentText
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $sql = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'Unable to start the MySQL client.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $writeFailure = $null
    try {
        $process.StandardInput.Write($sql)
    } catch {
        $writeFailure = $_.Exception.Message
    }
    try { $process.StandardInput.Close() } catch { }
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = $process.ExitCode
    $process.Dispose()

    if ($stdout) { Write-Host $stdout.TrimEnd() }
    if ($stderr) { Write-Host $stderr.TrimEnd() }
    if ($writeFailure) {
        throw "MySQL client closed its input early: $writeFailure (exit $exitCode). Review the generated SQL and server output above."
    }
    if ($exitCode -ne 0) {
        throw "MySQL script failed (exit $exitCode). Review the generated SQL and server output above."
    }
}

function Get-AuditDatabaseMetadata {
    param(
        [string]$LoginPath,
        [string]$UserName,
        [string]$HostName,
        [int]$Port = 3306
    )
    $serverRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port `
        -Query 'SELECT @@hostname, @@port, @@version')
    $server = @($serverRows[0] -split "`t")
    if ($server.Count -lt 3) { throw 'Could not read MySQL server identity.' }

    $schemaRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port `
        -Query "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('moneypal_fd','moneypal_customer','moneypal_product') ORDER BY SCHEMA_NAME")
    if ($schemaRows.Count -ne 3) {
        throw 'The connected MySQL server must contain moneypal_fd, moneypal_customer, and moneypal_product.'
    }

    $tableQuery = @'
SELECT t.TABLE_SCHEMA, t.TABLE_NAME,
       COALESCE(c.COLUMN_NAME, ''), COALESCE(c.DATA_TYPE, ''),
       COALESCE(c.ORDINAL_POSITION, 0), COALESCE(c.COLUMN_KEY, '')
FROM information_schema.TABLES AS t
LEFT JOIN information_schema.COLUMNS AS c
  ON c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME
WHERE t.TABLE_SCHEMA IN ('moneypal_fd','moneypal_customer','moneypal_product')
  AND t.TABLE_TYPE='BASE TABLE'
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME, c.ORDINAL_POSITION
'@
    $tableRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port -Query $tableQuery)
    $tables = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)
    foreach ($line in $tableRows) {
        if (-not $line) { continue }
        $part = $line.Split([char]9)
        if ($part.Count -lt 6) { throw "Unexpected table metadata row: $line" }
        $key = @($part[0], $part[1]) -join [char]0
        if (-not $tables.ContainsKey($key)) {
            $tables[$key] = [pscustomobject]@{
                Schema = $part[0]
                Name = $part[1]
                Columns = [System.Collections.Generic.List[object]]::new()
                Triggers = [System.Collections.Generic.List[object]]::new()
            }
        }
        if ($part[2]) {
            $tables[$key].Columns.Add([pscustomobject]@{
                Name = $part[2]
                Type = $part[3].ToLowerInvariant()
                Ordinal = [int]$part[4]
                Key = $part[5]
            })
        }
    }

    $triggerQuery = @'
SELECT TRIGGER_SCHEMA, EVENT_OBJECT_TABLE, TRIGGER_NAME,
       EVENT_MANIPULATION, ACTION_TIMING, ACTION_ORDER
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA IN ('moneypal_fd','moneypal_customer','moneypal_product')
ORDER BY TRIGGER_SCHEMA, EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION, ACTION_ORDER
'@
    $triggerRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port -Query $triggerQuery)
    foreach ($line in $triggerRows) {
        if (-not $line) { continue }
        $part = $line.Split([char]9)
        if ($part.Count -lt 6) { throw "Unexpected trigger metadata row: $line" }
        $key = @($part[0], $part[1]) -join [char]0
        if ($tables.ContainsKey($key)) {
            $tables[$key].Triggers.Add([pscustomobject]@{
                Name = $part[2]
                Event = $part[3]
                Timing = $part[4]
                ActionOrder = [int]$part[5]
            })
        }
    }

    $routineRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port `
        -Query "SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='moneypal_fd' AND ROUTINE_NAME='_codex_audit_columns_backfill_v1' AND ROUTINE_TYPE='PROCEDURE'")

    $manifestKey = @($script:AuditManifestSchema, $script:AuditManifestTable) -join [char]0
    $manifestExisted = $tables.ContainsKey($manifestKey)
    if ($manifestExisted) {
        $manifestColumns = @($tables[$manifestKey].Columns | ForEach-Object Name)
        foreach ($required in @('run_id','target_schema','target_table','object_type','object_name','created_at')) {
            if ($manifestColumns -notcontains $required) {
                throw "Existing manifest table has an unexpected shape; review $manifestKey manually."
            }
        }
        $manifestQuery = "SELECT target_schema,target_table,object_type,object_name FROM ``$script:AuditManifestSchema``.``$script:AuditManifestTable`` WHERE run_id='$script:AuditRunId'"
        $manifestRows = @(Invoke-AuditMysqlQuery -LoginPath $LoginPath -UserName $UserName -HostName $HostName -Port $Port -Query $manifestQuery)
    } else {
        $manifestRows = @()
        $tables[$manifestKey] = [pscustomobject]@{
            Schema = $script:AuditManifestSchema
            Name = $script:AuditManifestTable
            Columns = [System.Collections.Generic.List[object]]::new()
            Triggers = [System.Collections.Generic.List[object]]::new()
        }
        $synthetic = $tables[$manifestKey]
        $ordinal = 1
        foreach ($column in @(
            @('run_id','varchar','PRI'), @('target_schema','varchar','PRI'), @('target_table','varchar','PRI'),
            @('object_type','varchar','PRI'), @('object_name','varchar','PRI'), @('created_at','datetime','')
        )) {
            $synthetic.Columns.Add([pscustomobject]@{
                Name = $column[0]; Type = $column[1]; Ordinal = $ordinal; Key = $column[2]
            })
            $ordinal++
        }
    }

    $manifest = [System.Collections.Generic.Dictionary[string,bool]]::new([System.StringComparer]::Ordinal)
    foreach ($line in $manifestRows) {
        if (-not $line) { continue }
        $part = $line.Split([char]9)
        if ($part.Count -ge 4) {
            $manifest[@($part[0], $part[1], $part[2], $part[3]) -join [char]0] = $true
        }
    }

    return [pscustomobject]@{
        Host = $server[0]
        Port = $server[1]
        Version = $server[2]
        SchemaIds = $script:AuditSchemaIds
        Tables = @($tables.Values | Sort-Object Schema, Name)
        Routines = @($routineRows | Where-Object { $_ })
        Manifest = $manifest
        ManifestExisted = $manifestExisted
    }
}

Export-ModuleMember -Function ConvertTo-AuditSqlIdentifier, ConvertTo-AuditSqlLiteral, Get-AuditTriggerName, Invoke-AuditMysqlQuery, Invoke-AuditMysqlFile, Get-AuditDatabaseMetadata
