[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$StaticPreviewMapRoot = '',
    [string]$RuntimeMapRoot = '',
    [string]$BrowserSmokeScriptPath = '',
    [string]$OutputDir = '',
    [switch]$RequireMirrorRoots
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

function Normalize-OptionalPath {
    param(
        [string]$Path
    )
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ''
    }
    return [System.IO.Path]::GetFullPath($Path)
}

function Find-ResourceCommandMatches {
    param(
        [string]$Root,
        [string]$Pattern
    )
    $extensions = @(
        '.html',
        '.css',
        '.js',
        '.mjs',
        '.json',
        '.svg',
        '.txt',
        '.md'
    )
    $files = Get-ChildItem -LiteralPath $Root -Recurse -File -ErrorAction Stop |
        Where-Object { $extensions -contains $_.Extension.ToLowerInvariant() }
    $matches = New-Object System.Collections.Generic.List[object]
    foreach ($file in $files) {
        $fileMatches = Select-String -LiteralPath $file.FullName -Pattern $Pattern -AllMatches -ErrorAction SilentlyContinue
        foreach ($match in $fileMatches) {
            $matches.Add([ordered]@{
                path = $file.FullName
                line = $match.LineNumber
                text = $match.Line.Trim()
            })
        }
    }
    return $matches.ToArray()
}

function Test-BrowserSmokeContract {
    param(
        [string]$ScriptPath
    )
    $checks = New-Object System.Collections.Generic.List[object]
    $errors = New-Object System.Collections.Generic.List[string]
    if (-not (Test-Path -LiteralPath $ScriptPath)) {
        $errors.Add("Browser smoke script missing: $ScriptPath")
        return [pscustomobject]@{
            path = $ScriptPath
            status = 'error'
            checks = @()
            errors = $errors.ToArray()
        }
    }

    $content = [System.IO.File]::ReadAllText($ScriptPath, [System.Text.Encoding]::UTF8)
    $requiredPatterns = [ordered]@{
        commandUiRemovedPassFlag = 'commandUiRemoved=true'
        removedPanelAssertion = 'Resource district command UI was not fully removed'
        staleResourceCommandStateAssertion = 'resourceCommandMessage'
        staleOpenResourceCommandActionAssertion = 'open-resource-command'
    }
    foreach ($entry in $requiredPatterns.GetEnumerator()) {
        $present = $content.Contains($entry.Value)
        $checks.Add([ordered]@{
            name = $entry.Key
            expected = $entry.Value
            present = $present
        })
        if (-not $present) {
            $errors.Add("Browser smoke contract check missing: $($entry.Key) -> $($entry.Value)")
        }
    }

    $stalePassFlag = 'confirmUi=true'
    $hasStalePassFlag = $content.Contains($stalePassFlag)
    $checks.Add([ordered]@{
        name = 'staleConfirmUiPassFlagAbsent'
        expected = $stalePassFlag
        present = $hasStalePassFlag
    })
    if ($hasStalePassFlag) {
        $errors.Add("Browser smoke still contains stale pass flag: $stalePassFlag")
    }

    return [pscustomobject]@{
        path = $ScriptPath
        status = if ($errors.Count -eq 0) { 'ok' } else { 'error' }
        checks = $checks.ToArray()
        errors = $errors.ToArray()
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$workspaceRoot = Split-Path $projectRootPath -Parent

$sourceMapRoot = Join-Path $projectRootPath 'src\main\resources\web\map'
if ([string]::IsNullOrWhiteSpace($StaticPreviewMapRoot)) {
    $StaticPreviewMapRoot = Join-Path $workspaceRoot 'map'
}
if ([string]::IsNullOrWhiteSpace($RuntimeMapRoot)) {
    $RuntimeMapRoot = Join-Path $workspaceRoot 'test-server-paper-1.21.11\plugins\map'
}
if ([string]::IsNullOrWhiteSpace($BrowserSmokeScriptPath)) {
    $BrowserSmokeScriptPath = Join-Path $projectRootPath 'scripts\smoke-starcore-map-browser.mjs'
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $projectRootPath ('target\map-hud-contract-checks\check-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$outputDirPath = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $outputDirPath | Out-Null

$pattern = 'resourceCommand|open-resource-command|nationDetailOpenCommand|section-resource-command|resource-command'
$roots = @(
    [ordered]@{ label = 'source'; path = $sourceMapRoot; required = $true },
    [ordered]@{ label = 'static-preview'; path = $StaticPreviewMapRoot; required = $RequireMirrorRoots.IsPresent },
    [ordered]@{ label = 'runtime-map'; path = $RuntimeMapRoot; required = $RequireMirrorRoots.IsPresent }
)

$scanResults = New-Object System.Collections.Generic.List[object]
$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
foreach ($root in $roots) {
    $path = Normalize-OptionalPath -Path $root.path
    if (-not (Test-Path -LiteralPath $path)) {
        $message = "Map HUD contract root missing: $($root.label) -> $path"
        if ($root.required) {
            $errors.Add($message)
        } else {
            $warnings.Add($message)
        }
        $scanResults.Add([ordered]@{
            label = $root.label
            path = $path
            status = 'missing'
            required = [bool]$root.required
            matchCount = 0
            matches = @()
        })
        continue
    }

    $matches = Find-ResourceCommandMatches -Root $path -Pattern $pattern
    if ($matches.Count -gt 0) {
        $errors.Add("Map HUD contract root contains stale resource-command tokens: $($root.label) -> $path ($($matches.Count) match(es))")
    }
    $scanResults.Add([ordered]@{
        label = $root.label
        path = $path
        status = if ($matches.Count -eq 0) { 'ok' } else { 'error' }
        required = [bool]$root.required
        matchCount = $matches.Count
        matches = @($matches)
    })
}

$browserSmoke = Test-BrowserSmokeContract -ScriptPath ([System.IO.Path]::GetFullPath($BrowserSmokeScriptPath))
foreach ($error in $browserSmoke.errors) {
    $errors.Add($error)
}

$status = if ($errors.Count -eq 0) {
    if ($warnings.Count -eq 0) { 'ok' } else { 'warning' }
} else {
    'error'
}

$summaryPath = Join-Path $outputDirPath 'map-hud-contract-summary.json'
$summary = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = $status
    contract = 'intel-only-resource-district'
    projectRoot = $projectRootPath
    pattern = $pattern
    requireMirrorRoots = $RequireMirrorRoots.IsPresent
    roots = $scanResults.ToArray()
    browserSmoke = $browserSmoke
    errors = $errors.ToArray()
    warnings = $warnings.ToArray()
    summaryPath = $summaryPath
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

[pscustomobject]$summary | ConvertTo-Json -Depth 8
