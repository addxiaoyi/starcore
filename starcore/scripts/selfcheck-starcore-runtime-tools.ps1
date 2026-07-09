[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$WorkDir = '',
    [int]$TcpTimeoutMs = 150
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-PowerShellJsonScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$Label
    )
    $output = & powershell -ExecutionPolicy Bypass -File $ScriptPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
    return (($output | Out-String) | ConvertFrom-Json)
}

function Read-JsonFile {
    param(
        [string]$Path
    )
    return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $parent = Split-Path $Path -Parent
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        Ensure-Directory -Path $parent | Out-Null
    }
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

function Join-ArrayOrEmpty {
    param(
        [object]$Value
    )
    if ($null -eq $Value) {
        return @()
    }
    return @($Value)
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$workRootPath = if ([string]::IsNullOrWhiteSpace($WorkDir)) {
    Ensure-Directory -Path (Join-Path $projectRootPath ('target\runtime-tool-selfchecks\selfcheck-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
} else {
    Ensure-Directory -Path ([System.IO.Path]::GetFullPath($WorkDir))
}

$backupScript = Join-Path $projectRootPath 'scripts\backup-starcore-runtime.ps1'
$restoreScript = Join-Path $projectRootPath 'scripts\restore-starcore-runtime.ps1'
$databaseCheckScript = Join-Path $projectRootPath 'scripts\check-starcore-database-settings.ps1'
$protectorApiReferenceSyncScript = Join-Path $projectRootPath 'scripts\sync-protectorapi-reference.ps1'
$protectorApiReferenceCheckScript = Join-Path $projectRootPath 'scripts\check-protectorapi-reference.ps1'

$sourceDataDir = Ensure-Directory -Path (Join-Path $workRootPath 'source\plugins\STARCORE')
$sourceMapDir = Ensure-Directory -Path (Join-Path $workRootPath 'source\plugins\map')
$restoreDataDir = Ensure-Directory -Path (Join-Path $workRootPath 'restore-target\plugins\STARCORE')
$restoreMapDir = Ensure-Directory -Path (Join-Path $workRootPath 'restore-target\plugins\map')
$mysqlCheckDataDir = Ensure-Directory -Path (Join-Path $workRootPath 'mysql-check\plugins\STARCORE')
$backupOutputDir = Ensure-Directory -Path (Join-Path $workRootPath 'runtime-backups')
$restoreWorkDir = Ensure-Directory -Path (Join-Path $workRootPath 'runtime-restores')

$sqliteConfig = @'
locale: zh_cn
database:
  enabled: true
  fail-fast: false
  type: sqlite
  sqlite:
    file: starcore.db
map:
  export-directory: ..
'@
Write-TextFile -Path (Join-Path $sourceDataDir 'config.yml') -Content $sqliteConfig
Write-TextFile -Path (Join-Path $sourceDataDir 'messages_zh_cn.yml') -Content "test: ok`n"
Write-TextFile -Path (Join-Path $sourceDataDir 'starcore.db') -Content 'sqlite-main'
Write-TextFile -Path (Join-Path $sourceDataDir 'starcore.db-wal') -Content 'sqlite-wal'
Write-TextFile -Path (Join-Path $sourceDataDir 'starcore.db-shm') -Content 'sqlite-shm'
Write-TextFile -Path (Join-Path $sourceDataDir 'runtime-marker.txt') -Content 'fresh-runtime'
Write-TextFile -Path (Join-Path $sourceMapDir 'index.html') -Content '<html>map</html>'
Write-TextFile -Path (Join-Path $sourceMapDir 'snapshot.json') -Content '{"ok":true}'
Write-TextFile -Path (Join-Path $restoreDataDir 'restore-old.txt') -Content 'old-data'
Write-TextFile -Path (Join-Path $restoreMapDir 'restore-old-map.txt') -Content 'old-map'

$backupResult = Invoke-PowerShellJsonScript -ScriptPath $backupScript -Arguments @(
    '-ProjectRoot', $projectRootPath,
    '-DataDir', $sourceDataDir,
    '-MapExportDir', $sourceMapDir,
    '-OutputDir', $backupOutputDir,
    '-AsZip'
) -Label 'Runtime backup selfcheck'

Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($backupResult.zipPath)) -Message 'Backup selfcheck did not produce a zip path.'
Assert-True -Condition (Test-Path -LiteralPath $backupResult.zipPath) -Message "Backup zip was not created: $($backupResult.zipPath)"

$restoreResult = Invoke-PowerShellJsonScript -ScriptPath $restoreScript -Arguments @(
    '-ProjectRoot', $projectRootPath,
    '-BackupPath', $backupResult.zipPath,
    '-DataDir', $restoreDataDir,
    '-MapExportDir', $restoreMapDir,
    '-WorkDir', $restoreWorkDir,
    '-ReplaceExisting'
) -Label 'Runtime restore selfcheck'

$restoreSummary = Read-JsonFile -Path $restoreResult.summaryPath
Assert-True -Condition ($restoreSummary.replaceExisting -eq $true) -Message 'Restore summary did not record replaceExisting=true.'
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($restoreSummary.safeguardRoot)) -Message 'Restore selfcheck did not produce a safeguard directory.'
Assert-True -Condition (Test-Path -LiteralPath (Join-Path $restoreDataDir 'runtime-marker.txt')) -Message 'Restored STARCORE data is missing runtime-marker.txt.'
Assert-True -Condition (Test-Path -LiteralPath (Join-Path $restoreMapDir 'index.html')) -Message 'Restored map export is missing index.html.'
Assert-True -Condition (Test-Path -LiteralPath (Join-Path $restoreSummary.safeguardedDataDir 'restore-old.txt')) -Message 'Previous STARCORE data was not moved into safeguard storage.'
Assert-True -Condition (Test-Path -LiteralPath (Join-Path $restoreSummary.safeguardedMapDir 'restore-old-map.txt')) -Message 'Previous map export was not moved into safeguard storage.'

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$unusedMysqlPort = $listener.LocalEndpoint.Port
$listener.Stop()

$mysqlConfig = @"
locale: zh_cn
database:
  enabled: true
  fail-fast: false
  type: mysql
  mysql:
    host: 127.0.0.1
    port: $unusedMysqlPort
    database: starcore
    username: starcore
    password: test-password
    parameters: useUnicode=true&characterEncoding=utf8
"@
Write-TextFile -Path (Join-Path $mysqlCheckDataDir 'config.yml') -Content $mysqlConfig
Write-TextFile -Path (Join-Path $mysqlCheckDataDir 'starcore.db') -Content 'stale-main'
Write-TextFile -Path (Join-Path $mysqlCheckDataDir 'starcore.db-wal') -Content 'stale-wal'
Write-TextFile -Path (Join-Path $mysqlCheckDataDir 'starcore.db-shm') -Content 'stale-shm'

$databaseCheckResult = Invoke-PowerShellJsonScript -ScriptPath $databaseCheckScript -Arguments @(
    '-ProjectRoot', $projectRootPath,
    '-DataDir', $mysqlCheckDataDir,
    '-TcpTimeoutMs', [string]$TcpTimeoutMs
) -Label 'Database precheck selfcheck'

$protectorApiReferenceSyncResult = Invoke-PowerShellJsonScript -ScriptPath $protectorApiReferenceSyncScript -Arguments @(
    '-ProjectRoot', $projectRootPath
) -Label 'ProtectorAPI reference sync selfcheck'

$protectorApiReferenceCheckResult = Invoke-PowerShellJsonScript -ScriptPath $protectorApiReferenceCheckScript -Arguments @(
    '-ProjectRoot', $projectRootPath
) -Label 'ProtectorAPI reference selfcheck'

$mysqlWarnings = Join-ArrayOrEmpty -Value $databaseCheckResult.warnings
$mysqlStaleFiles = Join-ArrayOrEmpty -Value $databaseCheckResult.details.mysql.staleSqliteFiles

Assert-True -Condition ($databaseCheckResult.status -eq 'warning') -Message "Expected MySQL precheck status=warning, got $($databaseCheckResult.status)"
Assert-True -Condition ($databaseCheckResult.details.type -eq 'mysql') -Message "Expected MySQL precheck type=mysql, got $($databaseCheckResult.details.type)"
Assert-True -Condition ($databaseCheckResult.details.mysql.tcpReachable -eq $false) -Message 'Expected MySQL selfcheck TCP probe to be unreachable.'
Assert-True -Condition ($mysqlStaleFiles.Count -ge 1) -Message 'Expected MySQL selfcheck to report stale SQLite files.'
Assert-True -Condition ((Join-ArrayOrEmpty -Value $databaseCheckResult.errors).Count -eq 0) -Message 'Expected MySQL selfcheck to finish without hard errors.'

$summary = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = 'ok'
    projectRoot = $projectRootPath
    workRoot = $workRootPath
    restoreReplaceExisting = [ordered]@{
        passed = $true
        backupZipPath = $backupResult.zipPath
        restoreSummaryPath = $restoreResult.summaryPath
        safeguardRoot = $restoreSummary.safeguardRoot
        restoredDataDir = $restoreResult.dataDir
        restoredMapDir = $restoreResult.mapExportDir
    }
    mysqlPrecheck = [ordered]@{
        passed = $true
        status = $databaseCheckResult.status
        tcpReachable = $databaseCheckResult.details.mysql.tcpReachable
        warnings = $mysqlWarnings
        staleSqliteFiles = $mysqlStaleFiles
        configPath = $databaseCheckResult.details.configPath
    }
    protectorApiReferenceSync = [ordered]@{
        passed = ($protectorApiReferenceSyncResult.status -eq 'ok' -or $protectorApiReferenceSyncResult.status -eq 'warning')
        status = $protectorApiReferenceSyncResult.status
        summaryPath = $protectorApiReferenceSyncResult.summaryPath
        fetched = $protectorApiReferenceSyncResult.fetched
        fastForwarded = $protectorApiReferenceSyncResult.fastForwarded
        referenceHead = $protectorApiReferenceSyncResult.reference.head
        referenceOriginHead = $protectorApiReferenceSyncResult.reference.originHead
        referenceRemote = $protectorApiReferenceSyncResult.reference.remote
        warnings = (Join-ArrayOrEmpty -Value $protectorApiReferenceSyncResult.warnings)
    }
    protectorApiReferenceCheck = [ordered]@{
        passed = ($protectorApiReferenceCheckResult.status -eq 'ok')
        status = $protectorApiReferenceCheckResult.status
        summaryPath = $protectorApiReferenceCheckResult.summaryPath
        referenceHead = $protectorApiReferenceCheckResult.reference.head
        referenceRemote = $protectorApiReferenceCheckResult.reference.remote
    }
    notes = @(
        'This selfcheck intentionally validates the restore -ReplaceExisting path against a generated runtime backup zip.',
        'The MySQL precheck branch intentionally uses an unused localhost port so the script proves warning-mode behavior without needing a real MySQL server.',
        'The ProtectorAPI reference sync selfcheck verifies the local reference checkout can still be fetched and summarized before deeper contract checks.',
        'The ProtectorAPI reference selfcheck verifies the local reference checkout and STARCORE bridge contract stay aligned on the narrow reflection surface.'
    )
}

$summaryPath = Join-Path $workRootPath 'runtime-tool-selfcheck-summary.json'
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

[pscustomobject]@{
    status = 'ok'
    workRoot = $workRootPath
    summaryPath = $summaryPath
    backupZipPath = $backupResult.zipPath
    restoreSummaryPath = $restoreResult.summaryPath
} | ConvertTo-Json -Depth 6
