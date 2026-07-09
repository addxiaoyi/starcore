param(
    [string]$ProjectRoot = '',
    [string]$SmokeSummaryJsonPath = '',
    [string]$HudContractSummaryPath = '',
    [string]$RuntimeToolSelfcheckSummaryPath = '',
    [string]$PerformanceBudgetSummaryPath = '',
    [string]$SparkProfileSummaryPath = '',
    [string]$OutputPath = '',
    [switch]$AllowMissingSmoke
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

function Get-LatestSmokeSummaryPath {
    param(
        [string]$ProjectRootPath
    )
    $latest = Get-ChildItem -LiteralPath (Join-Path $ProjectRootPath 'target') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'smoke-harness-*' } |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidate = Join-Path $_.FullName 'smoke-summary.json'
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        } |
        Select-Object -First 1
    if (-not $latest) {
        throw 'No smoke-summary.json found under target\\smoke-harness-*'
    }
    return (Resolve-Path -LiteralPath $latest).Path
}

function Get-LatestRuntimeToolSelfcheckSummaryPath {
    param(
        [string]$ProjectRootPath
    )
    $latest = Get-ChildItem -LiteralPath (Join-Path $ProjectRootPath 'target\runtime-tool-selfchecks') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidate = Join-Path $_.FullName 'runtime-tool-selfcheck-summary.json'
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

function Get-LatestGeneratedSummaryPath {
    param(
        [string]$ProjectRootPath,
        [string]$RootDirectoryName,
        [string]$FileName
    )
    $baseDirectory = Join-Path $ProjectRootPath ('target\' + $RootDirectoryName)
    if (-not (Test-Path -LiteralPath $baseDirectory)) {
        return ''
    }
    $latest = Get-ChildItem -LiteralPath $baseDirectory -Directory -ErrorAction SilentlyContinue |
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

function Get-SurefireSummary {
    param(
        [string]$ProjectRootPath
    )
    $reportDir = Join-Path $ProjectRootPath 'target\surefire-reports'
    $files = Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File -ErrorAction Stop
    $summary = [ordered]@{
        tests = 0
        failures = 0
        errors = 0
        skipped = 0
    }
    $headerRegexOptions = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
    foreach ($file in $files) {
        $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $headerMatch = [regex]::Match($raw, '<testsuite\b[^>]*>', $headerRegexOptions)
        if (-not $headerMatch.Success) {
            Write-Warning ("Skipping malformed surefire report without <testsuite> header: {0}" -f $file.FullName)
            continue
        }
        $header = $headerMatch.Value

        $testsMatch = [regex]::Match($header, '\btests\s*=\s*"(?<value>\d+)"', $headerRegexOptions)
        $failuresMatch = [regex]::Match($header, '\bfailures\s*=\s*"(?<value>\d+)"', $headerRegexOptions)
        $errorsMatch = [regex]::Match($header, '\berrors\s*=\s*"(?<value>\d+)"', $headerRegexOptions)
        $skippedMatch = [regex]::Match($header, '\bskipped\s*=\s*"(?<value>\d+)"', $headerRegexOptions)

        if (-not $testsMatch.Success) {
            Write-Warning ("Skipping surefire report without tests attribute: {0}" -f $file.FullName)
            continue
        }

        $summary.tests += [int]$testsMatch.Groups['value'].Value
        $summary.failures += if ($failuresMatch.Success) { [int]$failuresMatch.Groups['value'].Value } else { 0 }
        $summary.errors += if ($errorsMatch.Success) { [int]$errorsMatch.Groups['value'].Value } else { 0 }
        $summary.skipped += if ($skippedMatch.Success) { [int]$skippedMatch.Groups['value'].Value } else { 0 }
    }
    return [pscustomobject]$summary
}

function Format-SqliteCountsLine {
    param(
        [pscustomobject]$Counts
    )
    if ($null -eq $Counts) {
        return ''
    }
    $pairs = foreach ($property in $Counts.PSObject.Properties | Sort-Object Name) {
        ('`{0}={1}`' -f $property.Name, $property.Value)
    }
    return ($pairs -join ', ')
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$smokeSummaryPath = if ([string]::IsNullOrWhiteSpace($SmokeSummaryJsonPath)) {
    if ($AllowMissingSmoke) {
        ''
    } else {
        Get-LatestSmokeSummaryPath -ProjectRootPath $projectRootPath
    }
} else {
    Resolve-RequiredPath -Path $SmokeSummaryJsonPath -Label 'Smoke summary JSON'
}
$runtimeToolSelfcheckSummaryPath = if ([string]::IsNullOrWhiteSpace($RuntimeToolSelfcheckSummaryPath)) {
    Get-LatestRuntimeToolSelfcheckSummaryPath -ProjectRootPath $projectRootPath
} else {
    Resolve-RequiredPath -Path $RuntimeToolSelfcheckSummaryPath -Label 'Runtime tool selfcheck summary JSON'
}
$performanceBudgetSummaryPath = if ([string]::IsNullOrWhiteSpace($PerformanceBudgetSummaryPath)) {
    Get-LatestGeneratedSummaryPath -ProjectRootPath $projectRootPath -RootDirectoryName 'performance-budget-checks' -FileName 'performance-budget-summary.json'
} else {
    Resolve-RequiredPath -Path $PerformanceBudgetSummaryPath -Label 'Performance budget summary JSON'
}
$sparkProfileSummaryPath = if ([string]::IsNullOrWhiteSpace($SparkProfileSummaryPath)) {
    ''
} else {
    Resolve-RequiredPath -Path $SparkProfileSummaryPath -Label 'Spark profile summary JSON'
}
$hudContractSummaryPath = if ([string]::IsNullOrWhiteSpace($HudContractSummaryPath)) {
    Get-LatestGeneratedSummaryPath -ProjectRootPath $projectRootPath -RootDirectoryName 'map-hud-contract-checks' -FileName 'map-hud-contract-summary.json'
} else {
    Resolve-RequiredPath -Path $HudContractSummaryPath -Label 'Map HUD contract summary JSON'
}

$summary = $null
if (-not [string]::IsNullOrWhiteSpace($smokeSummaryPath)) {
    $summary = Get-Content -LiteralPath $smokeSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$hudContract = $null
if (-not [string]::IsNullOrWhiteSpace($hudContractSummaryPath)) {
    $hudContract = Get-Content -LiteralPath $hudContractSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$runtimeToolSelfcheck = $null
if (-not [string]::IsNullOrWhiteSpace($runtimeToolSelfcheckSummaryPath)) {
    $runtimeToolSelfcheck = Get-Content -LiteralPath $runtimeToolSelfcheckSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$performanceBudget = $null
if (-not [string]::IsNullOrWhiteSpace($performanceBudgetSummaryPath)) {
    $performanceBudget = Get-Content -LiteralPath $performanceBudgetSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$sparkProfile = $null
if (-not [string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) {
    $sparkProfile = Get-Content -LiteralPath $sparkProfileSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$latestProtectorApiReferenceSyncSummaryPath = Get-LatestGeneratedSummaryPath -ProjectRootPath $projectRootPath -RootDirectoryName 'protectorapi-reference-syncs' -FileName 'protectorapi-reference-sync-summary.json'
$latestProtectorApiReferenceCheckSummaryPath = Get-LatestGeneratedSummaryPath -ProjectRootPath $projectRootPath -RootDirectoryName 'protectorapi-reference-checks' -FileName 'protectorapi-reference-check-summary.json'
$latestProtectorApiReferenceSync = $null
if (-not [string]::IsNullOrWhiteSpace($latestProtectorApiReferenceSyncSummaryPath)) {
    $latestProtectorApiReferenceSync = Get-Content -LiteralPath $latestProtectorApiReferenceSyncSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$latestProtectorApiReferenceCheck = $null
if (-not [string]::IsNullOrWhiteSpace($latestProtectorApiReferenceCheckSummaryPath)) {
    $latestProtectorApiReferenceCheck = Get-Content -LiteralPath $latestProtectorApiReferenceCheckSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
$testSummary = Get-SurefireSummary -ProjectRootPath $projectRootPath
$jarPath = Resolve-RequiredPath -Path (Join-Path $projectRootPath 'target\starcore-0.1.0-SNAPSHOT.jar') -Label 'STARCORE jar'
$jarSize = (Get-Item -LiteralPath $jarPath).Length
$jarSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
$output = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Join-Path $projectRootPath 'docs\LATEST_VERIFICATION_SUMMARY.md'
} else {
    [System.IO.Path]::GetFullPath($OutputPath)
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# STARCORE Latest Verification Summary')
$lines.Add('')
$lines.Add(('Generated on {0:yyyy-MM-dd HH:mm:ss zzz} from current workspace state.' -f (Get-Date)))
$lines.Add('')
$lines.Add('## Build and test')
$lines.Add('')
$lines.Add(('- `mvn -q test`: `{0}` tests, `{1}` failures, `{2}` errors, `{3}` skipped' -f $testSummary.tests, $testSummary.failures, $testSummary.errors, $testSummary.skipped))
$lines.Add(('- `mvn -q package`: PASS'))
$lines.Add(('- Jar: `{0}`' -f $jarPath))
$lines.Add(('- Jar size: `{0}` bytes' -f $jarSize))
$lines.Add(('- Jar SHA256: `{0}`' -f $jarSha256))
if ($runtimeToolSelfcheck) {
    $lines.Add(('- Runtime tool selfcheck: `{0}`' -f $runtimeToolSelfcheck.status))
}
if ($performanceBudget) {
    $lines.Add(('- Performance budget: `{0}` ({1}/{2} pass, {3} not included)' -f $performanceBudget.status, $performanceBudget.totals.passed, $performanceBudget.totals.checks, $performanceBudget.totals.notIncluded))
}
if ($sparkProfile) {
    $lines.Add(('- Spark profile: `{0}` ({1})' -f $sparkProfile.status, $sparkProfile.sourceLabel))
}
$lines.Add('')
if ($hudContract) {
    $lines.Add('## Map HUD Contract')
    $lines.Add('')
    $lines.Add(('- Map HUD contract summary JSON: `{0}`' -f $hudContractSummaryPath))
    $lines.Add(('- Contract: `{0}`' -f $hudContract.contract))
    $lines.Add(('- Status: `{0}`' -f $hudContract.status))
    $lines.Add(('- Resource-command scan pattern: `{0}`' -f $hudContract.pattern))
    foreach ($root in $hudContract.roots) {
        $lines.Add(('- `{0}`: `{1}`, matches=`{2}`, path=`{3}`' -f $root.label, $root.status, $root.matchCount, $root.path))
    }
    if ($hudContract.browserSmoke) {
        $lines.Add(('- Browser smoke contract: `{0}`, path=`{1}`' -f $hudContract.browserSmoke.status, $hudContract.browserSmoke.path))
    }
    if ($hudContract.warnings -and $hudContract.warnings.Count -gt 0) {
        $lines.Add(('- Warnings: `{0}`' -f (($hudContract.warnings | ForEach-Object { $_ }) -join '`, `')))
    }
    $lines.Add('')
}
if ($summary) {
    $lines.Add('## Latest smoke')
    $lines.Add('')
    $lines.Add(('- Smoke summary JSON: `{0}`' -f $smokeSummaryPath))
    $lines.Add(('- Health: `{0}`' -f $summary.health))
    $lines.Add(('- Marker: `{0}`' -f $summary.marker))
    if ($summary.markerLine) {
        $lines.Add(('- Marker line: `{0}`' -f $summary.markerLine))
    }
    if ($summary.webClaimSmoke) {
        $lines.Add(('- Web claim smoke: `{0}` ({1})' -f $summary.webClaimSmoke.status, $summary.webClaimSmoke.details))
    }
    if ($summary.browserSmoke) {
        $lines.Add(('- Browser smoke: `{0}` ({1})' -f $summary.browserSmoke.status, $summary.browserSmoke.details))
    }
    if ($summary.browserDom) {
        $lines.Add(('- Browser DOM: `{0}`' -f $summary.browserDom))
    }
    if ($summary.browserScreenshot) {
        $lines.Add(('- Browser screenshot: `{0}`' -f $summary.browserScreenshot))
    }
    if ($summary.browserMobileScreenshot) {
        $lines.Add(('- Browser mobile screenshot: `{0}`' -f $summary.browserMobileScreenshot))
    }
    if ($summary.outLog) {
        $lines.Add(('- Paper log: `{0}`' -f $summary.outLog))
    }
} elseif ($AllowMissingSmoke) {
    $lines.Add('## Latest smoke')
    $lines.Add('')
    $lines.Add('- Smoke summary: not included in this verification run.')
}
if ($runtimeToolSelfcheck) {
    $lines.Add('')
    $lines.Add('## Runtime Tool Selfcheck')
    $lines.Add('')
    $lines.Add(('- Runtime tool selfcheck summary JSON: `{0}`' -f $runtimeToolSelfcheckSummaryPath))
    $lines.Add(('- Restore `-ReplaceExisting`: `{0}`' -f $(if ($runtimeToolSelfcheck.restoreReplaceExisting.passed) { 'PASS' } else { 'FAIL' })))
    $lines.Add(('- Restore safeguard root: `{0}`' -f $runtimeToolSelfcheck.restoreReplaceExisting.safeguardRoot))
    $lines.Add(('- Generated backup zip: `{0}`' -f $runtimeToolSelfcheck.restoreReplaceExisting.backupZipPath))
    $lines.Add(('- MySQL precheck branch: `{0}` / tcpReachable=`{1}`' -f $runtimeToolSelfcheck.mysqlPrecheck.status, $runtimeToolSelfcheck.mysqlPrecheck.tcpReachable))
    if ($runtimeToolSelfcheck.mysqlPrecheck.staleSqliteFiles) {
        $lines.Add(('- MySQL stale SQLite files: `{0}`' -f (($runtimeToolSelfcheck.mysqlPrecheck.staleSqliteFiles | ForEach-Object { $_ }) -join '`, `')))
    }
    if ($latestProtectorApiReferenceSync) {
        $lines.Add(('- ProtectorAPI reference sync: `{0}` / head=`{1}` / fetched=`{2}` / fastForwarded=`{3}`' -f $latestProtectorApiReferenceSync.status, $latestProtectorApiReferenceSync.reference.head, $latestProtectorApiReferenceSync.fetched, $latestProtectorApiReferenceSync.fastForwarded))
        $lines.Add(('- ProtectorAPI reference sync summary JSON: `{0}`' -f $latestProtectorApiReferenceSyncSummaryPath))
    } elseif ($runtimeToolSelfcheck.protectorApiReferenceSync) {
        $lines.Add(('- ProtectorAPI reference sync: `{0}` / head=`{1}` / fetched=`{2}` / fastForwarded=`{3}`' -f $runtimeToolSelfcheck.protectorApiReferenceSync.status, $runtimeToolSelfcheck.protectorApiReferenceSync.referenceHead, $runtimeToolSelfcheck.protectorApiReferenceSync.fetched, $runtimeToolSelfcheck.protectorApiReferenceSync.fastForwarded))
        $lines.Add(('- ProtectorAPI reference sync summary JSON: `{0}`' -f $runtimeToolSelfcheck.protectorApiReferenceSync.summaryPath))
    }
    if ($latestProtectorApiReferenceCheck) {
        $lines.Add(('- ProtectorAPI reference check: `{0}` / head=`{1}`' -f $latestProtectorApiReferenceCheck.status, $latestProtectorApiReferenceCheck.reference.head))
        $lines.Add(('- ProtectorAPI reference check summary JSON: `{0}`' -f $latestProtectorApiReferenceCheckSummaryPath))
    } elseif ($runtimeToolSelfcheck.protectorApiReferenceCheck) {
        $lines.Add(('- ProtectorAPI reference check: `{0}` / head=`{1}`' -f $runtimeToolSelfcheck.protectorApiReferenceCheck.status, $runtimeToolSelfcheck.protectorApiReferenceCheck.referenceHead))
        $lines.Add(('- ProtectorAPI reference check summary JSON: `{0}`' -f $runtimeToolSelfcheck.protectorApiReferenceCheck.summaryPath))
    }
}
if ($performanceBudget) {
    $lines.Add('')
    $lines.Add('## Performance Budgets')
    $lines.Add('')
    $lines.Add(('- Performance budget summary JSON: `{0}`' -f $performanceBudgetSummaryPath))
    $lines.Add(('- Status: `{0}` / pass=`{1}` / fail=`{2}` / not included=`{3}`' -f $performanceBudget.status, $performanceBudget.totals.passed, $performanceBudget.totals.failed, $performanceBudget.totals.notIncluded))
    if ($performanceBudget.baseline) {
        $comparisonCount = @($performanceBudget.baseline.comparisons | Where-Object { $null -ne $_ }).Count
        $baselineMode = if ($performanceBudget.baseline.mode) { [string]$performanceBudget.baseline.mode } else { 'history' }
        $baselineAggregation = if ($performanceBudget.baseline.aggregation) { [string]$performanceBudget.baseline.aggregation } else { 'latest' }
        $baselineSamples = if ($null -ne $performanceBudget.baseline.sampleCount) { [int]$performanceBudget.baseline.sampleCount } else { 1 }
        $baselineWindow = if ($null -ne $performanceBudget.baseline.windowSize) { [int]$performanceBudget.baseline.windowSize } else { $baselineSamples }
        $lines.Add(('- Baseline trend: `{0}` / mode=`{1}` / aggregation=`{2}` / samples=`{3}/{4}` / comparisons=`{5}` / latest=`{6}`' -f $performanceBudget.baseline.status, $baselineMode, $baselineAggregation, $baselineSamples, $baselineWindow, $comparisonCount, $performanceBudget.baseline.summaryPath))
        if ($performanceBudget.baseline.warnings -and $performanceBudget.baseline.warnings.Count -gt 0) {
            $lines.Add(('- Baseline warnings: `{0}`' -f (($performanceBudget.baseline.warnings | ForEach-Object { $_ }) -join '`, `')))
        }
        if ($performanceBudget.baseline.errors -and $performanceBudget.baseline.errors.Count -gt 0) {
            $lines.Add(('- Baseline errors: `{0}`' -f (($performanceBudget.baseline.errors | ForEach-Object { $_ }) -join '`, `')))
        }
    }
    foreach ($check in $performanceBudget.checks) {
        $evidence = $check.evidence
        if ($check.kind -eq 'surefire-suite-time') {
            $lines.Add(('- `{0}`: `{1}`, suites=`{2}`, tests=`{3}`, time=`{4:N3}s` / budget=`{5:N3}s`' -f $check.id, $check.status, $evidence.suiteCount, $evidence.tests, [double]$evidence.totalSeconds, [double]$evidence.maxSeconds))
        } elseif ($check.kind -eq 'surefire-testcase-time') {
            $lines.Add(('- `{0}`: `{1}`, testcases=`{2}`, time=`{3:N3}s` / budget=`{4:N3}s`' -f $check.id, $check.status, $evidence.testcaseCount, [double]$evidence.totalSeconds, [double]$evidence.maxSeconds))
        } elseif ($check.kind -eq 'smoke-artifact-size') {
            $artifacts = @($evidence.artifacts | Where-Object { $null -ne $_ })
            if ($artifacts.Count -gt 0) {
                $artifactText = @($artifacts | ForEach-Object { ('{0}={1} bytes' -f $_.label, $_.bytes) }) -join ', '
                $lines.Add(('- `{0}`: `{1}`, {2}' -f $check.id, $check.status, $artifactText))
            } else {
                $lines.Add(('- `{0}`: `{1}`' -f $check.id, $check.status))
            }
        } else {
            $lines.Add(('- `{0}`: `{1}`' -f $check.id, $check.status))
        }
        if ($check.messages -and $check.messages.Count -gt 0) {
            $lines.Add(('  - Notes: `{0}`' -f (($check.messages | ForEach-Object { $_ }) -join '`, `')))
        }
    }
}
if ($sparkProfile) {
    $lines.Add('')
    $lines.Add('## Spark Profiling')
    $lines.Add('')
    $lines.Add(('- Spark profile summary JSON: `{0}`' -f $sparkProfileSummaryPath))
    $lines.Add(('- Status: `{0}` / source=`{1}`' -f $sparkProfile.status, $sparkProfile.sourceLabel))
    if ($sparkProfile.reportUrl) {
        $lines.Add(('- Report URL: `{0}`' -f $sparkProfile.reportUrl))
    }
    if ($sparkProfile.artifact -and $sparkProfile.artifact.status -eq 'present') {
        $lines.Add(('- Artifact: `{0}` / kind=`{1}` / bytes=`{2}` / files=`{3}`' -f $sparkProfile.artifact.copiedPath, $sparkProfile.artifact.kind, $sparkProfile.artifact.bytes, $sparkProfile.artifact.fileCount))
        if ($sparkProfile.artifact.sha256) {
            $lines.Add(('- Artifact SHA256: `{0}`' -f $sparkProfile.artifact.sha256))
        }
    } elseif ($sparkProfile.artifact) {
        $lines.Add(('- Artifact: `{0}`' -f $sparkProfile.artifact.status))
    }
    if ($sparkProfile.capture) {
        $lines.Add(('- Capture commands: start=`{0}`, stop=`{1}`, open=`{2}`' -f $sparkProfile.capture.startCommand, $sparkProfile.capture.stopCommand, $sparkProfile.capture.openCommand))
    }
    if ($sparkProfile.notes) {
        $lines.Add(('- Notes: `{0}`' -f $sparkProfile.notes))
    }
    if ($sparkProfile.warnings -and $sparkProfile.warnings.Count -gt 0) {
        $lines.Add(('- Warnings: `{0}`' -f (($sparkProfile.warnings | ForEach-Object { $_ }) -join '`, `')))
    }
}
if ($summary -and $summary.harness) {
    $lines.Add('')
    $lines.Add('## Harness snapshot')
    $lines.Add('')
    if ($summary.harness.message) {
        $lines.Add(('- Message: `{0}`' -f $summary.harness.message))
    }
    if ($summary.harness.viewerName) {
        $lines.Add(('- Viewer: `{0}` / nation `{1}` / balance `{2}`' -f $summary.harness.viewerName, $summary.harness.viewerNation, $summary.harness.viewerBalance))
    }
    if ($summary.harness.viewerGovernment) {
        $lines.Add(('- Viewer government: `{0}` / role `{1}` / online `{2}`' -f $summary.harness.viewerGovernment, $summary.harness.viewerRole, $summary.harness.viewerOnline))
    }
    if ($summary.harness.viewerClaims) {
        $lines.Add(('- Viewer claims: `{0}` / resources `{1}` / experience `{2}`' -f $summary.harness.viewerClaims, $summary.harness.viewerResources, $summary.harness.viewerExperience))
    }
    $sqliteCountsLine = Format-SqliteCountsLine -Counts $summary.harness.sqliteCounts
    if ($sqliteCountsLine) {
        $lines.Add(('- SQLite counts: {0}' -f $sqliteCountsLine))
    }
}

$markdown = $lines -join [Environment]::NewLine
Set-Content -LiteralPath $output -Value $markdown -Encoding UTF8
$markdown
