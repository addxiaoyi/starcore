param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$DataDir = '',
    [string]$MapExportDir = '',
    [string]$OutputDir = '',
    [switch]$IncludeCache,
    [switch]$SkipMapExport,
    [switch]$AsZip
)

$ErrorActionPreference = 'Stop'

function Resolve-RequiredPath {
    param(
        [string]$Path,
        [string]$Label
    )
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        throw "$Label not found: $Path"
    }
    return $resolved.Path
}

function Resolve-OptionalExistingPath {
    param(
        [string]$Path
    )
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return ''
    }
    return $resolved.Path
}

function Get-DefaultDataDir {
    param(
        [string]$ProjectRootPath
    )
    $candidates = @(
        (Join-Path (Split-Path $ProjectRootPath -Parent) 'test-server-paper-1.21.11\plugins\STARCORE')
    )

    $latestSmokeArtifact = Get-ChildItem -LiteralPath (Join-Path $ProjectRootPath 'target') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'smoke-harness-*' } |
        Sort-Object Name -Descending |
        ForEach-Object {
            $artifactDir = Join-Path $_.FullName 'STARCORE-artifact'
            if (Test-Path -LiteralPath $artifactDir) {
                return $artifactDir
            }
        } |
        Select-Object -First 1
    if ($latestSmokeArtifact) {
        $candidates += $latestSmokeArtifact
    }

    foreach ($candidate in $candidates) {
        $resolved = Resolve-OptionalExistingPath -Path $candidate
        if (-not [string]::IsNullOrWhiteSpace($resolved)) {
            return $resolved
        }
    }

    throw "Default STARCORE data directory not found. Pass -DataDir explicitly. Tried: $($candidates -join '; ')"
}

function Get-ConfigScalarValue {
    param(
        [string]$ConfigPath,
        [string]$Key,
        [string]$DefaultValue
    )
    if (-not (Test-Path -LiteralPath $ConfigPath)) {
        return $DefaultValue
    }

    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*:\s*(?<value>.+?)\s*$'
    foreach ($line in Get-Content -LiteralPath $ConfigPath -Encoding UTF8) {
        if ($line -match $pattern) {
            $rawValue = $Matches['value'].Trim()
            if ($rawValue.Contains('#')) {
                $rawValue = $rawValue.Substring(0, $rawValue.IndexOf('#')).Trim()
            }
            if (($rawValue.StartsWith('"') -and $rawValue.EndsWith('"')) -or ($rawValue.StartsWith("'") -and $rawValue.EndsWith("'"))) {
                $rawValue = $rawValue.Substring(1, $rawValue.Length - 2)
            }
            if ([string]::IsNullOrWhiteSpace($rawValue)) {
                return $DefaultValue
            }
            return $rawValue
        }
    }

    return $DefaultValue
}

function Get-DefaultMapExportDir {
    param(
        [string]$StarCoreDataDir
    )
    $configPath = Join-Path $StarCoreDataDir 'config.yml'
    $configuredExportDirectory = Get-ConfigScalarValue -ConfigPath $configPath -Key 'export-directory' -DefaultValue '..'
    $resolvedFromConfig = (Join-Path ([System.IO.Path]::GetFullPath($StarCoreDataDir)) $configuredExportDirectory)
    $resolvedMapDir = [System.IO.Path]::GetFullPath((Join-Path $resolvedFromConfig 'map'))
    if (Test-Path -LiteralPath $resolvedMapDir) {
        return $resolvedMapDir
    }

    return $resolvedMapDir
}

function Ensure-Directory {
    param(
        [string]$Path
    )
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    return (Resolve-Path -LiteralPath $Path).Path
}

function Copy-DirectoryItem {
    param(
        [System.IO.FileSystemInfo]$Item,
        [string]$DestinationDirectory
    )
    $destination = Join-Path $DestinationDirectory $Item.Name
    if ($Item.PSIsContainer) {
        Copy-Item -LiteralPath $Item.FullName -Destination $destination -Recurse -Force
    } else {
        Copy-Item -LiteralPath $Item.FullName -Destination $destination -Force
    }
}

function Get-RelativeEntries {
    param(
        [string]$BasePath
    )
    if (-not (Test-Path -LiteralPath $BasePath)) {
        return @()
    }
    $baseFullPath = [System.IO.Path]::GetFullPath($BasePath)
    if (-not $baseFullPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $baseFullPath += [System.IO.Path]::DirectorySeparatorChar
    }
    $baseUri = [System.Uri]::new($baseFullPath)
    $items = Get-ChildItem -LiteralPath $BasePath -Recurse -Force -ErrorAction SilentlyContinue
    $relative = foreach ($item in $items) {
        $itemUri = [System.Uri]::new($item.FullName)
        [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($itemUri).ToString()).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    }
    return @($relative | Sort-Object -Unique)
}

$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$dataDirPath = if ([string]::IsNullOrWhiteSpace($DataDir)) {
    Get-DefaultDataDir -ProjectRootPath $projectRootPath
} else {
    Resolve-RequiredPath -Path $DataDir -Label 'STARCORE data directory'
}

$outputRoot = if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    Join-Path $projectRootPath 'target\runtime-backups'
} else {
    [System.IO.Path]::GetFullPath($OutputDir)
}
$outputRoot = Ensure-Directory -Path $outputRoot

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupName = "starcore-runtime-backup-$timestamp"
$backupRoot = Ensure-Directory -Path (Join-Path $outputRoot $backupName)
$starCoreBackupRoot = Ensure-Directory -Path (Join-Path $backupRoot 'STARCORE')

$mapExportPath = ''
$mapExportDetectedByDefault = $false
if (-not $SkipMapExport) {
    if ([string]::IsNullOrWhiteSpace($MapExportDir)) {
        $defaultMapExportDir = Get-DefaultMapExportDir -StarCoreDataDir $dataDirPath
        $mapExportPath = Resolve-OptionalExistingPath -Path $defaultMapExportDir
        $mapExportDetectedByDefault = -not [string]::IsNullOrWhiteSpace($mapExportPath)
    } else {
        $mapExportPath = Resolve-RequiredPath -Path $MapExportDir -Label 'Map export directory'
    }
}

$copiedDataEntries = New-Object System.Collections.Generic.List[string]
foreach ($item in Get-ChildItem -LiteralPath $dataDirPath -Force) {
    if (-not $IncludeCache -and $item.PSIsContainer -and $item.Name -eq 'cache') {
        continue
    }
    Copy-DirectoryItem -Item $item -DestinationDirectory $starCoreBackupRoot
    $copiedDataEntries.Add($item.Name)
}

$mapBackupRoot = ''
$copiedMap = $false
if (-not [string]::IsNullOrWhiteSpace($mapExportPath)) {
    $mapBackupRoot = Ensure-Directory -Path (Join-Path $backupRoot 'map')
    foreach ($item in Get-ChildItem -LiteralPath $mapExportPath -Force) {
        Copy-DirectoryItem -Item $item -DestinationDirectory $mapBackupRoot
    }
    $copiedMap = $true
}

$mapRelativeEntries = @()
if ($copiedMap) {
    $mapRelativeEntries = @(Get-RelativeEntries -BasePath $mapBackupRoot)
}

$manifest = [ordered]@{
    createdAt = (Get-Date).ToString('o')
    projectRoot = $projectRootPath
    backupRoot = $backupRoot
    dataDir = $dataDirPath
    mapExportDir = $mapExportPath
    includeCache = [bool]$IncludeCache
    skipMapExport = [bool]$SkipMapExport
    mapExportDetectedByDefault = $mapExportDetectedByDefault
    notes = @(
        'Primary runtime data usually lives under plugins/STARCORE. This backup copies everything there except cache unless -IncludeCache is used.',
        'The default map export path is inferred from config.yml export-directory and usually resolves to plugins/map. Pass -MapExportDir when your server uses a custom export location.',
        'For SQLite, keep starcore.db, starcore.db-wal, and starcore.db-shm together when backing up or restoring.'
    )
    contents = [ordered]@{
        starcore = [ordered]@{
            path = $starCoreBackupRoot
            topLevelEntries = @($copiedDataEntries | Sort-Object)
            relativeEntries = @(Get-RelativeEntries -BasePath $starCoreBackupRoot)
            sqliteFiles = @(
                @('starcore.db', 'starcore.db-wal', 'starcore.db-shm') |
                    Where-Object { Test-Path -LiteralPath (Join-Path $starCoreBackupRoot $_) }
            )
            cacheIncluded = [bool]$IncludeCache
        }
        mapExport = [ordered]@{
            included = $copiedMap
            sourcePath = $mapExportPath
            backupPath = $mapBackupRoot
            relativeEntries = $mapRelativeEntries
        }
    }
}

$manifestPath = Join-Path $backupRoot 'backup-manifest.json'
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$zipPath = ''
if ($AsZip) {
    $zipPath = Join-Path $outputRoot "$backupName.zip"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -LiteralPath $backupRoot -DestinationPath $zipPath -Force
}

[pscustomobject]@{
    backupRoot = $backupRoot
    manifestPath = $manifestPath
    zipPath = $zipPath
    dataDir = $dataDirPath
    mapExportDir = $mapExportPath
    includeCache = [bool]$IncludeCache
    mapExportIncluded = $copiedMap
} | ConvertTo-Json -Depth 6
