[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [switch]$IncludeSmoke,
    [switch]$BrowserSmoke,
    [switch]$ProtectorApiSmoke,
    [switch]$BuildEvidencePack,
    [switch]$BuildReleaseChannelAssets,
    [int]$SmokeTimeoutSeconds = 360,
    [string]$ServerRoot = '',
    [string]$PaperJar = 'paper-1.21.11-69.jar',
    [string]$PaperApiJar = '',
    [string]$ProtectorApiJar = '',
    [string]$BrowserPath = '',
    [string]$PerformanceBudgetConfigPath = '',
    [string]$SparkProfileSummaryPath = '',
    [switch]$RequireRealSparkProfile,
    [switch]$AllowMissingHudMirrorRoots
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

function Invoke-StepCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$Label
    )
    Write-Host ('==> {0}' -f $Label)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

function Convert-ArgumentArrayToSplat {
    param(
        [string[]]$Arguments
    )
    $splat = @{}
    for ($index = 0; $index -lt $Arguments.Count; $index++) {
        $token = [string]$Arguments[$index]
        if (-not $token.StartsWith('-')) {
            continue
        }
        $name = $token.TrimStart('-')
        $nextIndex = $index + 1
        if ($nextIndex -lt $Arguments.Count -and -not ([string]$Arguments[$nextIndex]).StartsWith('-')) {
            $splat[$name] = [string]$Arguments[$nextIndex]
            $index++
        } else {
            $splat[$name] = $true
        }
    }
    return $splat
}

function Invoke-PowerShellJsonScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$Label
    )
    Write-Host ('==> {0}' -f $Label)
    $splat = Convert-ArgumentArrayToSplat -Arguments $Arguments
    $output = & $ScriptPath @splat
    return (($output | Out-String) | ConvertFrom-Json)
}

function Invoke-PowerShellStepScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$Label
    )
    Write-Host ('==> {0}' -f $Label)
    $splat = Convert-ArgumentArrayToSplat -Arguments $Arguments
    & $ScriptPath @splat
    if (-not $?) {
        throw "$Label failed."
    }
}

function Get-LatestFilePath {
    param(
        [string]$BaseDirectory,
        [string]$DirectoryPattern,
        [string]$FileName
    )
    $latest = Get-ChildItem -LiteralPath $BaseDirectory -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like $DirectoryPattern } |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidate = Join-Path $_.FullName $FileName
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        } |
        Select-Object -First 1

    if (-not $latest) {
        return ''
    }
    return (Resolve-Path -LiteralPath $latest).Path
}

function Test-HttpUrl {
    param(
        [string]$Url
    )
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $false
    }
    $uri = $null
    if (-not [System.Uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$uri)) {
        return $false
    }
    return $uri.Scheme -in @('http', 'https')
}

function Assert-SparkProfileSummary {
    param(
        [string]$SummaryPath,
        [bool]$RequireReal
    )
    if ([string]::IsNullOrWhiteSpace($SummaryPath)) {
        if ($RequireReal) {
            throw 'Real Spark profile gate failed: pass -SparkProfileSummaryPath with a production or staging spark-profile-summary.json.'
        }
        return $null
    }

    $summary = Get-Content -LiteralPath $SummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $errors = New-Object System.Collections.Generic.List[string]
    $status = if ($summary.status) { [string]$summary.status } else { '' }
    $sourceLabel = if ($summary.sourceLabel) { [string]$summary.sourceLabel } else { '' }

    if ([string]::IsNullOrWhiteSpace($status)) {
        $errors.Add('summary.status is missing')
    } elseif ($status -eq 'error') {
        $errors.Add('summary.status is error')
    }

    if ($RequireReal) {
        if ($status -ne 'ok') {
            $errors.Add("summary.status must be ok for a real release profile, got '$status'")
        }
        if ([string]::IsNullOrWhiteSpace($sourceLabel)) {
            $errors.Add('sourceLabel is missing')
        } elseif ($sourceLabel -in @('sample-verification', 'sample', 'script-check', 'manual')) {
            $errors.Add("sourceLabel '$sourceLabel' is reserved for samples or ambiguous manual imports")
        } elseif ($sourceLabel -match '(?i)(^|[-_\s])sample([-_\s]|$)') {
            $errors.Add("sourceLabel '$sourceLabel' still looks like a sample profile")
        }

        if (-not (Test-HttpUrl -Url ([string]$summary.reportUrl))) {
            $errors.Add('reportUrl must be an absolute http(s) spark report URL')
        }

        if (-not $summary.artifact -or [string]$summary.artifact.status -ne 'present') {
            $errors.Add('artifact.status must be present')
        } else {
            $artifactBytes = 0L
            [void][long]::TryParse(([string]$summary.artifact.bytes), [ref]$artifactBytes)
            $artifactFiles = 0
            [void][int]::TryParse(([string]$summary.artifact.fileCount), [ref]$artifactFiles)
            if ($artifactBytes -le 0) {
                $errors.Add('artifact.bytes must be greater than 0')
            }
            if ($artifactFiles -le 0) {
                $errors.Add('artifact.fileCount must be greater than 0')
            }
            if ([string]::IsNullOrWhiteSpace([string]$summary.artifact.copiedPath)) {
                $errors.Add('artifact.copiedPath is missing')
            } elseif (-not (Test-Path -LiteralPath ([string]$summary.artifact.copiedPath))) {
                $errors.Add("artifact.copiedPath does not exist: $($summary.artifact.copiedPath)")
            }
        }

        if (-not $summary.capture `
            -or [string]::IsNullOrWhiteSpace([string]$summary.capture.startCommand) `
            -or [string]::IsNullOrWhiteSpace([string]$summary.capture.stopCommand) `
            -or [string]::IsNullOrWhiteSpace([string]$summary.capture.openCommand)) {
            $errors.Add('capture.startCommand, capture.stopCommand, and capture.openCommand are required')
        }
    }

    if ($errors.Count -gt 0) {
        throw ('Real Spark profile gate failed: {0}' -f ($errors -join '; '))
    }
    return $summary
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$buildSummaryScript = Join-Path $projectRootPath 'scripts\build-latest-verification-summary.ps1'
$runtimeSelfcheckScript = Join-Path $projectRootPath 'scripts\selfcheck-starcore-runtime-tools.ps1'
$hudContractScript = Join-Path $projectRootPath 'scripts\check-map-hud-contract.ps1'
$performanceBudgetScript = Join-Path $projectRootPath 'scripts\check-starcore-performance-budget.ps1'
$smokeScript = Join-Path $projectRootPath 'scripts\smoke-starcore-paper-integration.ps1'
$releaseEvidenceScript = Join-Path $projectRootPath 'scripts\build-starcore-release-evidence-pack.ps1'
$releaseChannelAssetsScript = Join-Path $projectRootPath 'scripts\build-starcore-release-channel-assets.ps1'
$sparkProfileSummaryPath = if ([string]::IsNullOrWhiteSpace($SparkProfileSummaryPath)) {
    ''
} else {
    Resolve-RequiredPath -Path $SparkProfileSummaryPath -Label 'Spark profile summary JSON'
}
$sparkProfileSummary = Assert-SparkProfileSummary -SummaryPath $sparkProfileSummaryPath -RequireReal:$RequireRealSparkProfile.IsPresent

if (($BuildEvidencePack -or $BuildReleaseChannelAssets) -and -not $IncludeSmoke) {
    throw 'Building release evidence or release channel assets from the unified verify entrypoint requires -IncludeSmoke so the bundled smoke artifacts match this verification run.'
}

Invoke-StepCommand -FilePath 'mvn' -Arguments @('-q', 'test') -Label 'Run mvn -q test'
Invoke-StepCommand -FilePath 'mvn' -Arguments @('-q', 'package') -Label 'Run mvn -q package'

$hudContractArguments = @('-ProjectRoot', $projectRootPath)
if (-not $AllowMissingHudMirrorRoots) {
    $hudContractArguments += '-RequireMirrorRoots'
}
if (-not [string]::IsNullOrWhiteSpace($ServerRoot)) {
    $hudContractArguments += @('-RuntimeMapRoot', (Join-Path ([System.IO.Path]::GetFullPath($ServerRoot)) 'plugins\map'))
}
$hudContractResult = Invoke-PowerShellJsonScript `
    -ScriptPath $hudContractScript `
    -Arguments $hudContractArguments `
    -Label 'Run Map HUD contract check'
if ($hudContractResult.status -ne 'ok') {
    $errorSummary = if ($hudContractResult.errors) { ($hudContractResult.errors -join '; ') } else { "status=$($hudContractResult.status)" }
    throw "Map HUD contract check failed: $errorSummary"
}

$runtimeSelfcheckResult = Invoke-PowerShellJsonScript `
    -ScriptPath $runtimeSelfcheckScript `
    -Arguments @('-ProjectRoot', $projectRootPath) `
    -Label 'Run runtime tool selfcheck'

$smokeSummaryPath = ''
if ($IncludeSmoke) {
    $smokeArguments = @(
        '-ProjectRoot', $projectRootPath,
        '-TimeoutSeconds', [string]$SmokeTimeoutSeconds
    )
    if (-not [string]::IsNullOrWhiteSpace($ServerRoot)) {
        $smokeArguments += @('-ServerRoot', $ServerRoot)
    }
    if (-not [string]::IsNullOrWhiteSpace($PaperJar)) {
        $smokeArguments += @('-PaperJar', $PaperJar)
    }
    if (-not [string]::IsNullOrWhiteSpace($PaperApiJar)) {
        $smokeArguments += @('-PaperApiJar', $PaperApiJar)
    }
    if (-not [string]::IsNullOrWhiteSpace($ProtectorApiJar)) {
        $smokeArguments += @('-ProtectorApiJar', $ProtectorApiJar)
    }
    if (-not [string]::IsNullOrWhiteSpace($BrowserPath)) {
        $smokeArguments += @('-BrowserPath', $BrowserPath)
    }
    if ($ProtectorApiSmoke) {
        $smokeArguments += '-ProtectorApiSmoke'
    }
    if ($BrowserSmoke) {
        $smokeArguments += '-BrowserSmoke'
    }

    Invoke-StepCommand -FilePath 'powershell' -Arguments (@('-ExecutionPolicy', 'Bypass', '-File', $smokeScript) + $smokeArguments) -Label 'Run Paper smoke integration'
    $smokeSummaryPath = Get-LatestFilePath -BaseDirectory (Join-Path $projectRootPath 'target') -DirectoryPattern 'smoke-harness-*' -FileName 'smoke-summary.json'
    if ([string]::IsNullOrWhiteSpace($smokeSummaryPath)) {
        throw 'Smoke run completed but no smoke-summary.json was found.'
    }
}

$performanceBudgetArguments = @('-ProjectRoot', $projectRootPath)
if (-not [string]::IsNullOrWhiteSpace($PerformanceBudgetConfigPath)) {
    $performanceBudgetArguments += @('-BudgetConfigPath', $PerformanceBudgetConfigPath)
}
if ($IncludeSmoke) {
    $performanceBudgetArguments += @('-SmokeSummaryJsonPath', $smokeSummaryPath)
} else {
    $performanceBudgetArguments += '-AllowMissingSmoke'
}
$performanceBudgetResult = Invoke-PowerShellJsonScript `
    -ScriptPath $performanceBudgetScript `
    -Arguments $performanceBudgetArguments `
    -Label 'Run performance budget check'
if ($performanceBudgetResult.status -eq 'error') {
    $failedBudgets = @($performanceBudgetResult.checks | Where-Object { $_.status -eq 'fail' } | ForEach-Object { $_.id })
    throw "Performance budget check failed: $($failedBudgets -join ', ')"
}

$summaryArguments = @(
    '-ProjectRoot', $projectRootPath,
    '-HudContractSummaryPath', $hudContractResult.summaryPath,
    '-RuntimeToolSelfcheckSummaryPath', $runtimeSelfcheckResult.summaryPath,
    '-PerformanceBudgetSummaryPath', $performanceBudgetResult.summaryPath
)
if ($IncludeSmoke) {
    $summaryArguments += @('-SmokeSummaryJsonPath', $smokeSummaryPath)
} else {
    $summaryArguments += '-AllowMissingSmoke'
}
if (-not [string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) {
    $summaryArguments += @('-SparkProfileSummaryPath', $sparkProfileSummaryPath)
}

Invoke-PowerShellStepScript -ScriptPath $buildSummaryScript -Arguments $summaryArguments -Label 'Build latest verification summary'

$releaseEvidenceResult = $null
if ($BuildEvidencePack) {
    $releaseEvidenceArguments = @(
        '-ProjectRoot', $projectRootPath,
        '-SmokeSummaryJsonPath', $smokeSummaryPath,
        '-HudContractSummaryPath', $hudContractResult.summaryPath,
        '-RuntimeToolSelfcheckSummaryPath', $runtimeSelfcheckResult.summaryPath,
        '-PerformanceBudgetSummaryPath', $performanceBudgetResult.summaryPath,
        '-VerificationSummaryPath', (Join-Path $projectRootPath 'docs\LATEST_VERIFICATION_SUMMARY.md')
    )
    if (-not [string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) {
        $releaseEvidenceArguments += @('-SparkProfileSummaryPath', $sparkProfileSummaryPath)
    }
    $releaseEvidenceResult = Invoke-PowerShellJsonScript `
        -ScriptPath $releaseEvidenceScript `
        -Arguments $releaseEvidenceArguments `
        -Label 'Build release evidence pack'
}

$releaseChannelAssetsResult = $null
if ($BuildReleaseChannelAssets) {
    $releaseChannelAssetsArguments = @(
        '-ProjectRoot', $projectRootPath,
        '-SmokeSummaryJsonPath', $smokeSummaryPath,
        '-HudContractSummaryPath', $hudContractResult.summaryPath,
        '-PerformanceBudgetSummaryPath', $performanceBudgetResult.summaryPath,
        '-VerificationSummaryPath', (Join-Path $projectRootPath 'docs\LATEST_VERIFICATION_SUMMARY.md')
    )
    if (-not [string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) {
        $releaseChannelAssetsArguments += @('-SparkProfileSummaryPath', $sparkProfileSummaryPath)
    }
    $releaseChannelAssetsResult = Invoke-PowerShellJsonScript `
        -ScriptPath $releaseChannelAssetsScript `
        -Arguments $releaseChannelAssetsArguments `
        -Label 'Build release channel assets'
}

[pscustomobject]@{
    status = 'ok'
    projectRoot = $projectRootPath
    includeSmoke = $IncludeSmoke.IsPresent
    browserSmoke = $BrowserSmoke.IsPresent
    protectorApiSmoke = $ProtectorApiSmoke.IsPresent
    buildEvidencePack = $BuildEvidencePack.IsPresent
    buildReleaseChannelAssets = $BuildReleaseChannelAssets.IsPresent
    hudContractSummaryPath = $hudContractResult.summaryPath
    hudContract = $hudContractResult
    runtimeToolSelfcheckSummaryPath = $runtimeSelfcheckResult.summaryPath
    performanceBudgetSummaryPath = $performanceBudgetResult.summaryPath
    performanceBudget = $performanceBudgetResult
    sparkProfileSummaryPath = $sparkProfileSummaryPath
    sparkProfileGate = if ($RequireRealSparkProfile) { 'real-required' } elseif (-not [string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) { 'provided' } else { 'not-required' }
    sparkProfileSource = if ($sparkProfileSummary) { $sparkProfileSummary.sourceLabel } else { '' }
    smokeSummaryPath = $smokeSummaryPath
    verificationSummaryPath = (Join-Path $projectRootPath 'docs\LATEST_VERIFICATION_SUMMARY.md')
    jarPath = (Join-Path $projectRootPath 'target\starcore-0.1.0-SNAPSHOT.jar')
    releaseEvidence = $releaseEvidenceResult
    releaseChannelAssets = $releaseChannelAssetsResult
} | ConvertTo-Json -Depth 6
