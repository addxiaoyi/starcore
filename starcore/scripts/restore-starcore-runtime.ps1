[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$BackupPath = '',
    [string]$DataDir = '',
    [string]$MapExportDir = '',
    [string]$WorkDir = '',
    [switch]$SkipMapExport,
    [switch]$ReplaceExisting
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Resolve-BackupDirectory {
    param(
        [string]$ProjectRootPath,
        [string]$RequestedBackupPath,
        [string]$WorkRootPath
    )
    $candidate = $RequestedBackupPath
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = Get-LatestBackupPath -ProjectRootPath $ProjectRootPath
    }

    $resolvedCandidate = Resolve-RequiredPath -Path $candidate -Label 'Backup path'
    $item = Get-Item -LiteralPath $resolvedCandidate

    if (-not $item.PSIsContainer -and $item.Extension -eq '.zip') {
        $extractRoot = Ensure-Directory -Path (Join-Path $WorkRootPath ('extracted-' + [System.IO.Path]::GetFileNameWithoutExtension($item.Name) + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
        Expand-Archive -LiteralPath $item.FullName -DestinationPath $extractRoot -Force
        $directoryCandidate = Join-Path $extractRoot ([System.IO.Path]::GetFileNameWithoutExtension($item.Name))
        if (Test-Path -LiteralPath $directoryCandidate) {
            return [System.IO.Path]::GetFullPath($directoryCandidate)
        }
        return [System.IO.Path]::GetFullPath($extractRoot)
    }

    if ($item.PSIsContainer) {
        $starcoreDir = Join-Path $item.FullName 'STARCORE'
        if (Test-Path -LiteralPath $starcoreDir) {
            return [System.IO.Path]::GetFullPath($item.FullName)
        }

        $nested = Get-ChildItem -LiteralPath $item.FullName -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'starcore-runtime-backup-*' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'STARCORE')) } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($nested) {
            return [System.IO.Path]::GetFullPath($nested.FullName)
        }
    }

    throw "Backup directory does not contain a STARCORE restore payload: $resolvedCandidate"
}

function Move-ExistingPathToSafeguard {
    param(
        [string]$ExistingPath,
        [string]$SafeguardRoot,
        [string]$Label
    )
    if (-not (Test-Path -LiteralPath $ExistingPath)) {
        return ''
    }

    $targetPath = Join-Path $SafeguardRoot $Label
    $targetParent = Split-Path $targetPath -Parent
    if (-not [string]::IsNullOrWhiteSpace($targetParent)) {
        Ensure-Directory -Path $targetParent | Out-Null
    }
    Move-Item -LiteralPath $ExistingPath -Destination $targetPath
    return [System.IO.Path]::GetFullPath($targetPath)
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$workRootPath = if ([string]::IsNullOrWhiteSpace($WorkDir)) {
    Ensure-Directory -Path (Join-Path $projectRootPath 'target\runtime-restores')
} else {
    Ensure-Directory -Path ([System.IO.Path]::GetFullPath($WorkDir))
}

$backupRootPath = Resolve-BackupDirectory -ProjectRootPath $projectRootPath -RequestedBackupPath $BackupPath -WorkRootPath $workRootPath
$sourceDataDir = Resolve-RequiredPath -Path (Join-Path $backupRootPath 'STARCORE') -Label 'Backup STARCORE data directory'
$sourceMapDir = Resolve-OptionalExistingPath -Path (Join-Path $backupRootPath 'map')
$sourceConfigPath = Join-Path $sourceDataDir 'config.yml'

$dataDirPath = if ([string]::IsNullOrWhiteSpace($DataDir)) {
    [System.IO.Path]::GetFullPath((Get-DefaultServerDataDir -ProjectRootPath $projectRootPath))
} else {
    [System.IO.Path]::GetFullPath($DataDir)
}

$mapExportDestination = ''
if (-not $SkipMapExport) {
    if ([string]::IsNullOrWhiteSpace($MapExportDir)) {
        $mapExportDestination = Resolve-MapExportDirFromConfig -ConfigPath $sourceConfigPath -DataDirBase $dataDirPath
    } else {
        $mapExportDestination = [System.IO.Path]::GetFullPath($MapExportDir)
    }
}

$dataParent = Split-Path $dataDirPath -Parent
if (-not [string]::IsNullOrWhiteSpace($dataParent)) {
    Ensure-Directory -Path $dataParent | Out-Null
}

$mapParent = ''
if (-not [string]::IsNullOrWhiteSpace($mapExportDestination)) {
    $mapParent = Split-Path $mapExportDestination -Parent
    if (-not [string]::IsNullOrWhiteSpace($mapParent)) {
        Ensure-Directory -Path $mapParent | Out-Null
    }
}

$needsReplacement = (Test-Path -LiteralPath $dataDirPath) -or ((-not [string]::IsNullOrWhiteSpace($mapExportDestination)) -and (Test-Path -LiteralPath $mapExportDestination))
$safeguardRoot = ''
$safeguardedDataDir = ''
$safeguardedMapDir = ''
if ($needsReplacement) {
    if (-not $ReplaceExisting) {
        throw 'Destination runtime data already exists. Re-run with -ReplaceExisting after stopping the server and confirming you want to replace the current runtime state.'
    }

    $safeguardRoot = Ensure-Directory -Path (Join-Path $workRootPath ('pre-restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
    $safeguardedDataDir = Move-ExistingPathToSafeguard -ExistingPath $dataDirPath -SafeguardRoot $safeguardRoot -Label 'STARCORE-existing'
    if (-not [string]::IsNullOrWhiteSpace($mapExportDestination)) {
        $safeguardedMapDir = Move-ExistingPathToSafeguard -ExistingPath $mapExportDestination -SafeguardRoot $safeguardRoot -Label 'map-existing'
    }
}

Copy-DirectoryContents -SourceDirectory $sourceDataDir -DestinationDirectory $dataDirPath

$mapRestored = $false
if (-not $SkipMapExport -and -not [string]::IsNullOrWhiteSpace($sourceMapDir) -and -not [string]::IsNullOrWhiteSpace($mapExportDestination)) {
    Copy-DirectoryContents -SourceDirectory $sourceMapDir -DestinationDirectory $mapExportDestination
    $mapRestored = $true
}

$mapTopLevelEntries = @()
if ($mapRestored -and (Test-Path -LiteralPath $mapExportDestination)) {
    $mapTopLevelEntries = @((Get-ChildItem -LiteralPath $mapExportDestination -Force | Select-Object -ExpandProperty Name) | Sort-Object)
}

$manifest = [ordered]@{
    restoredAt = (Get-Date).ToString('o')
    backupRoot = $backupRootPath
    sourceDataDir = $sourceDataDir
    sourceMapDir = $sourceMapDir
    destinationDataDir = $dataDirPath
    destinationMapDir = $mapExportDestination
    replaceExisting = [bool]$ReplaceExisting
    mapRestored = $mapRestored
    safeguardRoot = $safeguardRoot
    safeguardedDataDir = $safeguardedDataDir
    safeguardedMapDir = $safeguardedMapDir
    restoredContents = [ordered]@{
        dataTopLevelEntries = @((Get-ChildItem -LiteralPath $dataDirPath -Force | Select-Object -ExpandProperty Name) | Sort-Object)
        mapTopLevelEntries = $mapTopLevelEntries
    }
}

$manifestPath = Join-Path $workRootPath ('restore-summary-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

[pscustomobject]@{
    backupRoot = $backupRootPath
    dataDir = $dataDirPath
    mapExportDir = $mapExportDestination
    mapRestored = $mapRestored
    safeguardRoot = $safeguardRoot
    summaryPath = $manifestPath
} | ConvertTo-Json -Depth 6
