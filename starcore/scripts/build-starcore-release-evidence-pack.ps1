[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$SmokeSummaryJsonPath = '',
    [string]$HudContractSummaryPath = '',
    [string]$RuntimeToolSelfcheckSummaryPath = '',
    [string]$PerformanceBudgetSummaryPath = '',
    [string]$SparkProfileSummaryPath = '',
    [string]$VerificationSummaryPath = '',
    [string]$ReleaseChannelPackPath = '',
    [string]$ReleaseChecklistPath = '',
    [string]$ReadmePath = '',
    [string]$OutputDirectory = '',
    [switch]$SkipZip
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

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

function Get-LatestDocPath {
    param(
        [string]$DocsDirectory,
        [string]$Pattern
    )
    $latest = Get-ChildItem -LiteralPath $DocsDirectory -File -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $latest) {
        return ''
    }
    return $latest.FullName
}

function Copy-ArtifactIfExists {
    param(
        [string]$SourcePath,
        [string]$DestinationDirectory
    )
    if ([string]::IsNullOrWhiteSpace($SourcePath)) {
        return ''
    }
    $resolved = Resolve-OptionalExistingPath -Path $SourcePath
    if ([string]::IsNullOrWhiteSpace($resolved)) {
        return ''
    }
    Ensure-Directory -Path $DestinationDirectory | Out-Null
    $destination = Join-Path $DestinationDirectory ([System.IO.Path]::GetFileName($resolved))
    $item = Get-Item -LiteralPath $resolved -Force
    if ($item.PSIsContainer) {
        Copy-Item -LiteralPath $resolved -Destination $destination -Recurse -Force
    } else {
        Copy-Item -LiteralPath $resolved -Destination $destination -Force
    }
    return $destination
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
    foreach ($file in $files) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $suite = $xml.testsuite
        $summary.tests += [int]$suite.tests
        $summary.failures += [int]$suite.failures
        $summary.errors += [int]$suite.errors
        $summary.skipped += [int]$suite.skipped
    }
    return [pscustomobject]$summary
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$docsDirectory = Join-Path $projectRootPath 'docs'
$targetDirectory = Join-Path $projectRootPath 'target'

$jarPath = Resolve-RequiredPath -Path (Join-Path $targetDirectory 'starcore-0.1.0-SNAPSHOT.jar') -Label 'STARCORE jar'
$verificationSummaryPath = if ([string]::IsNullOrWhiteSpace($VerificationSummaryPath)) {
    Resolve-RequiredPath -Path (Join-Path $docsDirectory 'LATEST_VERIFICATION_SUMMARY.md') -Label 'Latest verification summary'
} else {
    Resolve-RequiredPath -Path $VerificationSummaryPath -Label 'Latest verification summary'
}
$smokeSummaryPath = if ([string]::IsNullOrWhiteSpace($SmokeSummaryJsonPath)) {
    $latest = Get-LatestFilePath -BaseDirectory $targetDirectory -DirectoryPattern 'smoke-harness-*' -FileName 'smoke-summary.json'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No smoke-summary.json found under target\smoke-harness-*'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $SmokeSummaryJsonPath -Label 'Smoke summary JSON'
}
$hudContractSummaryPath = if ([string]::IsNullOrWhiteSpace($HudContractSummaryPath)) {
    $latest = Get-LatestFilePath -BaseDirectory (Join-Path $targetDirectory 'map-hud-contract-checks') -DirectoryPattern 'check-*' -FileName 'map-hud-contract-summary.json'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No map-hud-contract-summary.json found under target\map-hud-contract-checks\check-*'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $HudContractSummaryPath -Label 'Map HUD contract summary JSON'
}
$runtimeToolSelfcheckSummaryPath = if ([string]::IsNullOrWhiteSpace($RuntimeToolSelfcheckSummaryPath)) {
    $latest = Get-LatestFilePath -BaseDirectory (Join-Path $targetDirectory 'runtime-tool-selfchecks') -DirectoryPattern 'selfcheck-*' -FileName 'runtime-tool-selfcheck-summary.json'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No runtime-tool-selfcheck-summary.json found under target\runtime-tool-selfchecks\selfcheck-*'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $RuntimeToolSelfcheckSummaryPath -Label 'Runtime tool selfcheck summary JSON'
}
$performanceBudgetSummaryPath = if ([string]::IsNullOrWhiteSpace($PerformanceBudgetSummaryPath)) {
    $latest = Get-LatestFilePath -BaseDirectory (Join-Path $targetDirectory 'performance-budget-checks') -DirectoryPattern 'check-*' -FileName 'performance-budget-summary.json'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No performance-budget-summary.json found under target\performance-budget-checks\check-*'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $PerformanceBudgetSummaryPath -Label 'Performance budget summary JSON'
}
$sparkProfileSummaryPath = if ([string]::IsNullOrWhiteSpace($SparkProfileSummaryPath)) {
    ''
} else {
    Resolve-RequiredPath -Path $SparkProfileSummaryPath -Label 'Spark profile summary JSON'
}
$releaseChannelPackPath = if ([string]::IsNullOrWhiteSpace($ReleaseChannelPackPath)) {
    $latest = Get-LatestDocPath -DocsDirectory $docsDirectory -Pattern 'RELEASE_CHANNEL_PACK_*.md'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No RELEASE_CHANNEL_PACK_*.md found under docs'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $ReleaseChannelPackPath -Label 'Release channel pack'
}
$releaseChecklistPath = if ([string]::IsNullOrWhiteSpace($ReleaseChecklistPath)) {
    $latest = Get-LatestDocPath -DocsDirectory $docsDirectory -Pattern 'RELEASE_CHECKLIST_*.md'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No RELEASE_CHECKLIST_*.md found under docs'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $ReleaseChecklistPath -Label 'Release checklist'
}
$readmePath = if ([string]::IsNullOrWhiteSpace($ReadmePath)) {
    Resolve-RequiredPath -Path (Join-Path $projectRootPath 'README.md') -Label 'README'
} else {
    Resolve-RequiredPath -Path $ReadmePath -Label 'README'
}

$smokeSummary = Get-Content -LiteralPath $smokeSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$hudContractSummary = Get-Content -LiteralPath $hudContractSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$runtimeToolSelfcheckSummary = Get-Content -LiteralPath $runtimeToolSelfcheckSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$performanceBudgetSummary = Get-Content -LiteralPath $performanceBudgetSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$sparkProfileSummary = if ([string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) { $null } else { Get-Content -LiteralPath $sparkProfileSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json }
$testSummary = Get-SurefireSummary -ProjectRootPath $projectRootPath

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $targetDirectory ('release-evidence-{0}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$outputDirectoryPath = Ensure-Directory -Path $OutputDirectory
$docsOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'docs')
$smokeOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'smoke')
$contractsOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'contracts')
$performanceOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'performance')
$packageOutputDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'package')

$jarCopyPath = Copy-ArtifactIfExists -SourcePath $jarPath -DestinationDirectory $packageOutputDirectory
$verificationSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $verificationSummaryPath -DestinationDirectory $docsOutputDirectory
$releaseChannelPackCopyPath = Copy-ArtifactIfExists -SourcePath $releaseChannelPackPath -DestinationDirectory $docsOutputDirectory
$releaseChecklistCopyPath = Copy-ArtifactIfExists -SourcePath $releaseChecklistPath -DestinationDirectory $docsOutputDirectory
$readmeCopyPath = Copy-ArtifactIfExists -SourcePath $readmePath -DestinationDirectory $docsOutputDirectory
$smokeSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummaryPath -DestinationDirectory $smokeOutputDirectory
$hudContractSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $hudContractSummaryPath -DestinationDirectory $contractsOutputDirectory
$runtimeToolSelfcheckCopyPath = Copy-ArtifactIfExists -SourcePath $runtimeToolSelfcheckSummaryPath -DestinationDirectory $smokeOutputDirectory
$performanceBudgetSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $performanceBudgetSummaryPath -DestinationDirectory $performanceOutputDirectory
$sparkProfileSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $sparkProfileSummaryPath -DestinationDirectory $performanceOutputDirectory
$sparkProfileArtifactCopyPath = if ($sparkProfileSummary -and $sparkProfileSummary.artifact -and $sparkProfileSummary.artifact.status -eq 'present') {
    Copy-ArtifactIfExists -SourcePath $sparkProfileSummary.artifact.copiedPath -DestinationDirectory (Join-Path $performanceOutputDirectory 'spark-profile-artifact')
} else {
    ''
}
$browserDomCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserDom -DestinationDirectory $smokeOutputDirectory
$browserScreenshotCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserScreenshot -DestinationDirectory $smokeOutputDirectory
$browserMobileScreenshotCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserMobileScreenshot -DestinationDirectory $smokeOutputDirectory
$paperLogCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.outLog -DestinationDirectory $smokeOutputDirectory
$paperErrLogCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.errLog -DestinationDirectory $smokeOutputDirectory

$renderedSmokeSummaryPath = Join-Path $smokeOutputDirectory 'SMOKE_SUMMARY_RENDERED.md'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'render-starcore-smoke-summary.ps1') `
    -SummaryJsonPath $smokeSummaryPath `
    -OutputPath $renderedSmokeSummaryPath | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to render smoke summary markdown.'
}

$manifestPath = Join-Path $outputDirectoryPath 'release-evidence-manifest.json'
$manifest = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    projectRoot = $projectRootPath
    package = [ordered]@{
        jar = $jarCopyPath
        verificationSummary = $verificationSummaryCopyPath
        releaseChannelPack = $releaseChannelPackCopyPath
        releaseChecklist = $releaseChecklistCopyPath
        readme = $readmeCopyPath
    }
    smoke = [ordered]@{
        smokeSummaryJson = $smokeSummaryCopyPath
        smokeSummaryRendered = $renderedSmokeSummaryPath
        runtimeToolSelfcheckSummary = $runtimeToolSelfcheckCopyPath
        browserDom = $browserDomCopyPath
        browserScreenshot = $browserScreenshotCopyPath
        browserMobileScreenshot = $browserMobileScreenshotCopyPath
        paperLog = $paperLogCopyPath
        paperErrLog = $paperErrLogCopyPath
    }
    contracts = [ordered]@{
        mapHudContractSummary = $hudContractSummaryCopyPath
    }
    performance = [ordered]@{
        performanceBudgetSummary = $performanceBudgetSummaryCopyPath
        sparkProfileSummary = $sparkProfileSummaryCopyPath
        sparkProfileArtifact = $sparkProfileArtifactCopyPath
    }
    snapshot = [ordered]@{
        tests = $testSummary.tests
        failures = $testSummary.failures
        errors = $testSummary.errors
        skipped = $testSummary.skipped
        marker = $smokeSummary.marker
        browserSmoke = if ($smokeSummary.browserSmoke) { $smokeSummary.browserSmoke.status } else { '' }
        webClaimSmoke = if ($smokeSummary.webClaimSmoke) { $smokeSummary.webClaimSmoke.status } else { '' }
        mapHudContract = $hudContractSummary.status
        mapHudContractSourceMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'source' } | Select-Object -First 1).matchCount } else { '' }
        mapHudContractStaticPreviewMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'static-preview' } | Select-Object -First 1).matchCount } else { '' }
        mapHudContractRuntimeMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'runtime-map' } | Select-Object -First 1).matchCount } else { '' }
        mapHudBrowserSmokeContract = if ($hudContractSummary.browserSmoke) { $hudContractSummary.browserSmoke.status } else { '' }
        runtimeToolSelfcheckStatus = $runtimeToolSelfcheckSummary.status
        performanceBudget = $performanceBudgetSummary.status
        performanceBudgetBaseline = if ($performanceBudgetSummary.baseline) { $performanceBudgetSummary.baseline.status } else { '' }
        performanceBudgetPassed = $performanceBudgetSummary.totals.passed
        performanceBudgetFailed = $performanceBudgetSummary.totals.failed
        performanceBudgetNotIncluded = $performanceBudgetSummary.totals.notIncluded
        sparkProfile = if ($sparkProfileSummary) { $sparkProfileSummary.status } else { '' }
        sparkProfileSource = if ($sparkProfileSummary) { $sparkProfileSummary.sourceLabel } else { '' }
        sparkProfileArtifactBytes = if ($sparkProfileSummary -and $sparkProfileSummary.artifact) { $sparkProfileSummary.artifact.bytes } else { 0 }
        sparkProfileReportUrl = if ($sparkProfileSummary) { $sparkProfileSummary.reportUrl } else { '' }
        jarSizeBytes = (Get-Item -LiteralPath $jarPath).Length
    }
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$zipPath = ''
if (-not $SkipZip) {
    $zipPath = '{0}.zip' -f $outputDirectoryPath
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    $archiveInputs = Get-ChildItem -LiteralPath $outputDirectoryPath -Force | Select-Object -ExpandProperty FullName
    if (-not $archiveInputs) {
        throw "Release evidence output directory was empty: $outputDirectoryPath"
    }
    Compress-Archive -Path $archiveInputs -DestinationPath $zipPath -CompressionLevel Optimal
}

[pscustomobject]@{
    status = 'ok'
    outputDirectory = $outputDirectoryPath
    zipPath = $zipPath
    manifestPath = $manifestPath
    jarPath = $jarCopyPath
    verificationSummaryPath = $verificationSummaryCopyPath
    mapHudContractSummaryPath = $hudContractSummaryCopyPath
    performanceBudgetSummaryPath = $performanceBudgetSummaryCopyPath
    sparkProfileSummaryPath = $sparkProfileSummaryCopyPath
    smokeSummaryPath = $smokeSummaryCopyPath
    renderedSmokeSummaryPath = $renderedSmokeSummaryPath
    browserScreenshotPath = $browserScreenshotCopyPath
    browserMobileScreenshotPath = $browserMobileScreenshotCopyPath
    paperLogPath = $paperLogCopyPath
} | ConvertTo-Json -Depth 6
