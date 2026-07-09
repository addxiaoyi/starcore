param(
    [string]$ProjectRoot = '',
    [string]$DataDir = '',
    [int]$TcpTimeoutMs = 3000
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Get-NormalizedDatabaseType {
    param(
        [string]$RawType
    )
    if ([string]::IsNullOrWhiteSpace($RawType)) {
        return 'sqlite'
    }

    switch ($RawType.Trim().ToLowerInvariant()) {
        'mysql' { return 'mysql' }
        'mariadb' { return 'mysql' }
        default { return 'sqlite' }
    }
}

function Resolve-SqlitePath {
    param(
        [string]$DataDirPath,
        [string]$ConfiguredFile
    )
    $file = if ([string]::IsNullOrWhiteSpace($ConfiguredFile)) { 'starcore.db' } else { $ConfiguredFile.Trim() }
    return Resolve-ConfiguredPath -BaseDirectory $DataDirPath -ConfiguredPath $file
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$dataDirPath = if ([string]::IsNullOrWhiteSpace($DataDir)) {
    Get-DefaultExistingDataDir -ProjectRootPath $projectRootPath
} else {
    Resolve-RequiredPath -Path $DataDir -Label 'STARCORE data directory'
}

$configPath = Resolve-RequiredPath -Path (Join-Path $dataDirPath 'config.yml') -Label 'STARCORE config.yml'
$yaml = Read-SimpleYamlMap -ConfigPath $configPath

$databaseEnabled = ConvertTo-BooleanValue -Value (Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.enabled' -DefaultValue 'true') -DefaultValue $true
$databaseFailFast = ConvertTo-BooleanValue -Value (Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.fail-fast' -DefaultValue 'false') -DefaultValue $false
$databaseTypeRaw = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.type' -DefaultValue 'sqlite'
$databaseType = Get-NormalizedDatabaseType -RawType $databaseTypeRaw

$warnings = New-Object System.Collections.Generic.List[string]
$errors = New-Object System.Collections.Generic.List[string]
$details = [ordered]@{
    configPath = $configPath
    dataDir = $dataDirPath
    enabled = $databaseEnabled
    failFast = $databaseFailFast
    typeRaw = $databaseTypeRaw
    type = $databaseType
}

if (-not $databaseEnabled) {
    $warnings.Add('Database support is disabled. STARCORE will rely on file-based fallback paths where available.')
}

if ($databaseType -eq 'sqlite') {
    $sqliteFile = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.sqlite.file' -DefaultValue 'starcore.db'
    $sqlitePath = Resolve-SqlitePath -DataDirPath $dataDirPath -ConfiguredFile $sqliteFile
    $walPath = $sqlitePath + '-wal'
    $shmPath = $sqlitePath + '-shm'
    $sqliteExists = Test-Path -LiteralPath $sqlitePath

    if (-not $sqliteExists) {
        $warnings.Add("SQLite file does not exist yet: $sqlitePath")
    }

    $details.sqlite = [ordered]@{
        configuredFile = $sqliteFile
        resolvedPath = $sqlitePath
        exists = $sqliteExists
        sizeBytes = if ($sqliteExists) { (Get-Item -LiteralPath $sqlitePath).Length } else { 0 }
        walPath = $walPath
        walExists = (Test-Path -LiteralPath $walPath)
        shmPath = $shmPath
        shmExists = (Test-Path -LiteralPath $shmPath)
    }
} else {
    $mysqlHost = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.host' -DefaultValue '127.0.0.1'
    $mysqlPortRaw = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.port' -DefaultValue '3306'
    $mysqlDatabase = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.database' -DefaultValue 'starcore'
    $mysqlUsername = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.username' -DefaultValue 'starcore'
    $mysqlPassword = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.password' -DefaultValue ''
    $mysqlParameters = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'database.mysql.parameters' -DefaultValue ''

    $mysqlPort = 3306
    if (-not [int]::TryParse($mysqlPortRaw, [ref]$mysqlPort) -or $mysqlPort -lt 1 -or $mysqlPort -gt 65535) {
        $errors.Add("MySQL port is invalid: $mysqlPortRaw")
        $mysqlPort = 3306
    }
    if ([string]::IsNullOrWhiteSpace($mysqlHost)) {
        $errors.Add('MySQL host is blank.')
    }
    if ([string]::IsNullOrWhiteSpace($mysqlDatabase)) {
        $errors.Add('MySQL database name is blank.')
    }
    if ([string]::IsNullOrWhiteSpace($mysqlUsername)) {
        $errors.Add('MySQL username is blank.')
    }
    if (-not $databaseFailFast) {
        $warnings.Add('MySQL is configured but database.fail-fast=false. Production servers that require SQL should usually turn it on.')
    }

    $tcpReachable = $false
    if (-not [string]::IsNullOrWhiteSpace($mysqlHost) -and $mysqlPort -ge 1 -and $mysqlPort -le 65535) {
        $tcpReachable = Test-TcpEndpoint -EndpointHost $mysqlHost -Port $mysqlPort -TimeoutMs $TcpTimeoutMs
        if (-not $tcpReachable) {
            $warnings.Add("MySQL TCP endpoint did not respond within ${TcpTimeoutMs}ms: $mysqlHost`:$mysqlPort")
        }
    }

    $staleSqliteFiles = @('starcore.db', 'starcore.db-wal', 'starcore.db-shm') |
        Where-Object { Test-Path -LiteralPath (Join-Path $dataDirPath $_) }
    if ($staleSqliteFiles.Count -gt 0) {
        $warnings.Add('MySQL is active but legacy SQLite files are still present in the data directory.')
    }

    $details.mysql = [ordered]@{
        host = $mysqlHost
        port = $mysqlPort
        database = $mysqlDatabase
        username = $mysqlUsername
        passwordConfigured = (-not [string]::IsNullOrEmpty($mysqlPassword))
        parameters = $mysqlParameters
        tcpReachable = $tcpReachable
        staleSqliteFiles = $staleSqliteFiles
    }
}

$status = 'ok'
if ($errors.Count -gt 0) {
    $status = 'error'
} elseif ($warnings.Count -gt 0) {
    $status = 'warning'
}

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = $status
    details = $details
    warnings = @($warnings)
    errors = @($errors)
}

$json = $result | ConvertTo-Json -Depth 8
Write-Output $json

if ($errors.Count -gt 0) {
    exit 1
}
