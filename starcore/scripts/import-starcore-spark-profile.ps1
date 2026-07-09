[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$ReportPath = '',
    [string]$ReportUrl = '',
    [string]$SourceLabel = 'manual',
    [string]$Notes = '',
    [string]$StartCommand = '/spark profiler start --timeout 60',
    [string]$StopCommand = '/spark profiler stop',
    [string]$OpenCommand = '/spark profiler open',
    [string]$OutputDir = ''
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Test-HttpUrl {
    param(
        [string]$Url
    )
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $true
    }
    $uri = $null
    if (-not [System.Uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$uri)) {
        return $false
    }
    return $uri.Scheme -in @('http', 'https')
}

function Get-DirectorySize {
    param(
        [string]$Path
    )
    $total = 0L
    foreach ($file in Get-ChildItem -LiteralPath $Path -Recurse -File -Force -ErrorAction SilentlyContinue) {
        $total += $file.Length
    }
    return $total
}

function Copy-SparkArtifact {
    param(
        [string]$SourcePath,
        [string]$DestinationDirectory
    )
    $resolved = Resolve-RequiredPath -Path $SourcePath -Label 'Spark profile report'
    $item = Get-Item -LiteralPath $resolved -Force
    Ensure-Directory -Path $DestinationDirectory | Out-Null
    $destination = Join-Path $DestinationDirectory $item.Name
    if ($item.PSIsContainer) {
        Copy-Item -LiteralPath $item.FullName -Destination $destination -Recurse -Force
        return [ordered]@{
            status = 'present'
            kind = 'directory'
            inputPath = $item.FullName
            copiedPath = (Resolve-Path -LiteralPath $destination).Path
            fileName = $item.Name
            extension = ''
            bytes = Get-DirectorySize -Path $item.FullName
            fileCount = @(Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Force -ErrorAction SilentlyContinue).Count
            sha256 = ''
        }
    }

    Copy-Item -LiteralPath $item.FullName -Destination $destination -Force
    return [ordered]@{
        status = 'present'
        kind = 'file'
        inputPath = $item.FullName
        copiedPath = (Resolve-Path -LiteralPath $destination).Path
        fileName = $item.Name
        extension = $item.Extension
        bytes = $item.Length
        fileCount = 1
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $item.FullName).Hash
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
if ([string]::IsNullOrWhiteSpace($ReportPath) -and [string]::IsNullOrWhiteSpace($ReportUrl)) {
    throw 'Provide -ReportPath, -ReportUrl, or both to import spark profiling evidence.'
}
if (-not (Test-HttpUrl -Url $ReportUrl)) {
    throw "Spark report URL must be an absolute http(s) URL: $ReportUrl"
}

$targetDirectory = Join-Path $projectRootPath 'target'
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $targetDirectory ('spark-profile-imports\profile-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$outputDirPath = Ensure-Directory -Path $OutputDir
$artifactOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirPath 'artifact')

$warnings = New-Object System.Collections.Generic.List[string]
$artifact = [ordered]@{
    status = 'not_included'
    kind = ''
    inputPath = ''
    copiedPath = ''
    fileName = ''
    extension = ''
    bytes = 0
    fileCount = 0
    sha256 = ''
}
if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
    $artifact = Copy-SparkArtifact -SourcePath $ReportPath -DestinationDirectory $artifactOutputDirectory
} else {
    $warnings.Add('No local spark profiling artifact was imported; only the report URL is recorded.')
}

$status = if ($warnings.Count -gt 0) { 'warning' } else { 'ok' }
$summaryPath = Join-Path $outputDirPath 'spark-profile-summary.json'
$summary = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = $status
    projectRoot = $projectRootPath
    sourceLabel = if ([string]::IsNullOrWhiteSpace($SourceLabel)) { 'manual' } else { $SourceLabel }
    reportUrl = $ReportUrl
    notes = $Notes
    capture = [ordered]@{
        startCommand = $StartCommand
        stopCommand = $StopCommand
        openCommand = $OpenCommand
    }
    artifact = $artifact
    warnings = $warnings.ToArray()
    summaryPath = $summaryPath
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
[pscustomobject]$summary | ConvertTo-Json -Depth 8
