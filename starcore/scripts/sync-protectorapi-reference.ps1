[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$ReferenceRoot = '',
    [string]$RemoteUrl = 'https://github.com/LinsMinecraftStudio/ProtectorAPI.git',
    [string]$Branch = 'main',
    [switch]$SkipFetch,
    [switch]$FastForward,
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Get-GitValueOrEmpty {
    param(
        [string[]]$Arguments
    )
    try {
        $value = & git @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) {
            return ''
        }
        return (($value | Out-String).Trim())
    } catch {
        return ''
    }
}

function Invoke-GitOrThrow {
    param(
        [string[]]$Arguments,
        [string]$Label
    )
    $output = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $message = (($output | Out-String).Trim())
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = 'git exited with a non-zero code'
        }
        throw "$Label failed: $message"
    }
    return (($output | Out-String).Trim())
}

function Get-AheadBehind {
    param(
        [string]$RepositoryRoot,
        [string]$LeftRef,
        [string]$RightRef
    )
    $counts = Get-GitValueOrEmpty -Arguments @('-C', $RepositoryRoot, 'rev-list', '--left-right', '--count', ($LeftRef + '...' + $RightRef))
    if ([string]::IsNullOrWhiteSpace($counts)) {
        return [ordered]@{
            ahead = 0
            behind = 0
        }
    }

    $parts = $counts -split '\s+'
    if ($parts.Count -lt 2) {
        return [ordered]@{
            ahead = 0
            behind = 0
        }
    }

    return [ordered]@{
        ahead = [int]$parts[0]
        behind = [int]$parts[1]
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'

$referenceRootPath = if ([string]::IsNullOrWhiteSpace($ReferenceRoot)) {
    [System.IO.Path]::GetFullPath((Join-Path $projectRootPath 'references\ProtectorAPI'))
} else {
    [System.IO.Path]::GetFullPath($ReferenceRoot)
}
$referenceParentPath = Ensure-Directory -Path (Split-Path -Path $referenceRootPath -Parent)

$warnings = New-Object System.Collections.Generic.List[string]
$cloned = $false
$fetched = $false
$fastForwarded = $false
$fastForwardSkippedReason = ''

if (-not (Test-Path -LiteralPath $referenceRootPath)) {
    Invoke-GitOrThrow -Arguments @('clone', '--branch', $Branch, $RemoteUrl, $referenceRootPath) -Label 'Clone ProtectorAPI reference'
    $cloned = $true
} elseif (-not (Test-Path -LiteralPath (Join-Path $referenceRootPath '.git'))) {
    throw "ProtectorAPI reference root exists but is not a git repository: $referenceRootPath"
}

$remote = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'config', '--get', 'remote.origin.url')
if ([string]::IsNullOrWhiteSpace($remote)) {
    Invoke-GitOrThrow -Arguments @('-C', $referenceRootPath, 'remote', 'add', 'origin', $RemoteUrl) -Label 'Add ProtectorAPI origin remote'
    $remote = $RemoteUrl
} elseif ($remote -ne $RemoteUrl) {
    $warnings.Add("origin remote differs from expected: current='$remote', expected='$RemoteUrl'")
}

if (-not $SkipFetch) {
    Invoke-GitOrThrow -Arguments @('-C', $referenceRootPath, 'fetch', 'origin') -Label 'Fetch ProtectorAPI origin'
    $fetched = $true
}

$branchName = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', '--abbrev-ref', 'HEAD')
$head = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', 'HEAD')
$originHead = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', ('origin/' + $Branch))
$headDecorated = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'log', '--oneline', '-1', '--decorate')
$statusShort = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'status', '--short')
$clean = [string]::IsNullOrWhiteSpace($statusShort)

if ($FastForward) {
    if ([string]::IsNullOrWhiteSpace($originHead)) {
        $fastForwardSkippedReason = "missing origin/$Branch reference"
        $warnings.Add("fast-forward skipped: $fastForwardSkippedReason")
    } elseif (-not $clean) {
        $fastForwardSkippedReason = 'reference checkout has local changes'
        $warnings.Add("fast-forward skipped: $fastForwardSkippedReason")
    } elseif ($branchName -ne $Branch) {
        $fastForwardSkippedReason = "current branch is '$branchName', expected '$Branch'"
        $warnings.Add("fast-forward skipped: $fastForwardSkippedReason")
    } else {
        $ancestorCheck = & git -C $referenceRootPath merge-base --is-ancestor HEAD ('origin/' + $Branch)
        if ($LASTEXITCODE -eq 0) {
            Invoke-GitOrThrow -Arguments @('-C', $referenceRootPath, 'merge', '--ff-only', ('origin/' + $Branch)) -Label 'Fast-forward ProtectorAPI reference'
            $fastForwarded = $true
            $branchName = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', '--abbrev-ref', 'HEAD')
            $head = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', 'HEAD')
            $headDecorated = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'log', '--oneline', '-1', '--decorate')
            $statusShort = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'status', '--short')
            $clean = [string]::IsNullOrWhiteSpace($statusShort)
        } else {
            $fastForwardSkippedReason = 'local HEAD is not an ancestor of origin branch'
            $warnings.Add("fast-forward skipped: $fastForwardSkippedReason")
        }
    }
}

$aheadBehind = if ([string]::IsNullOrWhiteSpace($originHead)) {
    [ordered]@{
        ahead = 0
        behind = 0
    }
} else {
    Get-AheadBehind -RepositoryRoot $referenceRootPath -LeftRef 'HEAD' -RightRef ('origin/' + $Branch)
}

$outputDirectory = Ensure-Directory -Path (Join-Path $projectRootPath ('target\protectorapi-reference-syncs\sync-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
$summaryPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Join-Path $outputDirectory 'protectorapi-reference-sync-summary.json'
} else {
    [System.IO.Path]::GetFullPath($OutputPath)
}
$summaryParent = Split-Path -Path $summaryPath -Parent
if (-not [string]::IsNullOrWhiteSpace($summaryParent)) {
    Ensure-Directory -Path $summaryParent | Out-Null
}

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = if ($warnings.Count -eq 0) { 'ok' } else { 'warning' }
    projectRoot = $projectRootPath
    referenceRoot = $referenceRootPath
    summaryPath = $summaryPath
    cloned = $cloned
    fetched = $fetched
    fastForwarded = $fastForwarded
    fastForwardSkippedReason = $fastForwardSkippedReason
    warnings = @($warnings)
    reference = [ordered]@{
        expectedRemote = $RemoteUrl
        remote = $remote
        branch = $branchName
        requestedBranch = $Branch
        head = $head
        originHead = $originHead
        headDecorated = $headDecorated
        clean = $clean
        statusShort = $statusShort
        ahead = $aheadBehind.ahead
        behind = $aheadBehind.behind
    }
}

$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
$result | ConvertTo-Json -Depth 8
