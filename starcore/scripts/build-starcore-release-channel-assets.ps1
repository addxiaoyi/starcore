[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$ReleaseChannelPackPath = '',
    [string]$VerificationSummaryPath = '',
    [string]$SmokeSummaryJsonPath = '',
    [string]$HudContractSummaryPath = '',
    [string]$PerformanceBudgetSummaryPath = '',
    [string]$SparkProfileSummaryPath = '',
    [string]$ReadmePath = '',
    [string]$OutputDirectory = '',
    [switch]$SkipZip
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

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

function Get-MarkdownSectionContent {
    param(
        [string[]]$Lines,
        [string]$Heading
    )
    $headingLevel = ([regex]::Match($Heading, '^(#+)').Groups[1].Value.Length)
    $startIndex = -1
    for ($index = 0; $index -lt $Lines.Length; $index++) {
        if ($Lines[$index].Trim() -eq $Heading) {
            $startIndex = $index + 1
            break
        }
    }
    if ($startIndex -lt 0) {
        throw "Heading not found in release channel pack: $Heading"
    }

    $content = New-Object System.Collections.Generic.List[string]
    for ($index = $startIndex; $index -lt $Lines.Length; $index++) {
        $line = $Lines[$index]
        $trimmed = $line.Trim()
        $match = [regex]::Match($trimmed, '^(#+)\s+')
        if ($match.Success -and $match.Groups[1].Value.Length -le $headingLevel) {
            break
        }
        $content.Add($line)
    }

    return ($content -join [Environment]::NewLine).Trim()
}

function Unwrap-MarkdownText {
    param(
        [string]$Content
    )
    $rawContent = if ($null -eq $Content) { '' } else { $Content }
    $trimmed = $rawContent.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) {
        return ''
    }

    if ($trimmed.StartsWith('```') -and $trimmed.EndsWith('```')) {
        $lines = $trimmed -split "`r?`n"
        if ($lines.Length -ge 3) {
            return (($lines | Select-Object -Skip 1 | Select-Object -SkipLast 1) -join [Environment]::NewLine).Trim()
        }
    }

    if ($trimmed.StartsWith('`') -and $trimmed.EndsWith('`') -and $trimmed.IndexOf("`n") -lt 0 -and $trimmed.Length -ge 2) {
        return $trimmed.Substring(1, $trimmed.Length - 2).Trim()
    }

    return $trimmed
}

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )
    $directory = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        Ensure-Directory -Path $directory | Out-Null
    }
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$docsDirectory = Join-Path $projectRootPath 'docs'
$targetDirectory = Join-Path $projectRootPath 'target'

$releaseChannelPackPath = if ([string]::IsNullOrWhiteSpace($ReleaseChannelPackPath)) {
    $latest = Get-LatestDocPath -DocsDirectory $docsDirectory -Pattern 'RELEASE_CHANNEL_PACK_*.md'
    if ([string]::IsNullOrWhiteSpace($latest)) {
        throw 'No RELEASE_CHANNEL_PACK_*.md found under docs'
    }
    $latest
} else {
    Resolve-RequiredPath -Path $ReleaseChannelPackPath -Label 'Release channel pack'
}

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

$readmePath = if ([string]::IsNullOrWhiteSpace($ReadmePath)) {
    Resolve-RequiredPath -Path (Join-Path $projectRootPath 'README.md') -Label 'README'
} else {
    Resolve-RequiredPath -Path $ReadmePath -Label 'README'
}

$jarPath = Resolve-RequiredPath -Path (Join-Path $targetDirectory 'starcore-0.1.0-SNAPSHOT.jar') -Label 'STARCORE jar'
$testSummary = Get-SurefireSummary -ProjectRootPath $projectRootPath
$smokeSummary = Get-Content -LiteralPath $smokeSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$hudContractSummary = Get-Content -LiteralPath $hudContractSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$performanceBudgetSummary = Get-Content -LiteralPath $performanceBudgetSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$sparkProfileSummary = if ([string]::IsNullOrWhiteSpace($sparkProfileSummaryPath)) { $null } else { Get-Content -LiteralPath $sparkProfileSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json }
$packLines = Get-Content -LiteralPath $releaseChannelPackPath -Encoding UTF8

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $targetDirectory ('release-channel-assets-{0}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
}

$outputDirectoryPath = Ensure-Directory -Path $OutputDirectory
$modrinthDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'modrinth')
$hangarDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'hangar')
$spigotDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'spigotmc')
$changelogDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'changelogs')
$assetDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'assets')
$referenceDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'references')
$packageDirectory = Ensure-Directory -Path (Join-Path $outputDirectoryPath 'package')

$subtitle = Unwrap-MarkdownText (Get-MarkdownSectionContent -Lines $packLines -Heading '### 标题副描述')
$shortDescription = Unwrap-MarkdownText (Get-MarkdownSectionContent -Lines $packLines -Heading '### 简短描述')
$modrinthLongDescription = Get-MarkdownSectionContent -Lines $packLines -Heading '### 长描述'
$modrinthTags = Get-MarkdownSectionContent -Lines $packLines -Heading '### 推荐标签'
$hangarOverview = Get-MarkdownSectionContent -Lines $packLines -Heading '### 概述'
$hangarFeatures = Get-MarkdownSectionContent -Lines $packLines -Heading '### 功能列表'
$hangarInstall = Get-MarkdownSectionContent -Lines $packLines -Heading '### 安装说明'
$spigotSummary = Get-MarkdownSectionContent -Lines $packLines -Heading '### 资源简介'
$spigotHighlights = Get-MarkdownSectionContent -Lines $packLines -Heading '### 亮点'
$spigotServerFit = Get-MarkdownSectionContent -Lines $packLines -Heading '### 适合的服务器'
$firstPublicChangelog = Unwrap-MarkdownText (Get-MarkdownSectionContent -Lines $packLines -Heading '### 首发版本模板')
$maintenanceChangelog = Unwrap-MarkdownText (Get-MarkdownSectionContent -Lines $packLines -Heading '### 小版本更新模板')
$oneLinePositioning = Get-MarkdownSectionContent -Lines $packLines -Heading '## 一句话定位'
$coreSellingPoints = Get-MarkdownSectionContent -Lines $packLines -Heading '## 核心卖点'

$jarCopyPath = Copy-ArtifactIfExists -SourcePath $jarPath -DestinationDirectory $packageDirectory
$verificationSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $verificationSummaryPath -DestinationDirectory $referenceDirectory
$releaseChannelPackCopyPath = Copy-ArtifactIfExists -SourcePath $releaseChannelPackPath -DestinationDirectory $referenceDirectory
$readmeCopyPath = Copy-ArtifactIfExists -SourcePath $readmePath -DestinationDirectory $referenceDirectory
$smokeSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummaryPath -DestinationDirectory $referenceDirectory
$hudContractSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $hudContractSummaryPath -DestinationDirectory $referenceDirectory
$performanceBudgetSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $performanceBudgetSummaryPath -DestinationDirectory $referenceDirectory
$sparkProfileSummaryCopyPath = Copy-ArtifactIfExists -SourcePath $sparkProfileSummaryPath -DestinationDirectory $referenceDirectory
$sparkProfileArtifactCopyPath = if ($sparkProfileSummary -and $sparkProfileSummary.artifact -and $sparkProfileSummary.artifact.status -eq 'present') {
    Copy-ArtifactIfExists -SourcePath $sparkProfileSummary.artifact.copiedPath -DestinationDirectory (Join-Path $referenceDirectory 'spark-profile-artifact')
} else {
    ''
}
$browserScreenshotCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserScreenshot -DestinationDirectory $assetDirectory
$browserMobileScreenshotCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserMobileScreenshot -DestinationDirectory $assetDirectory
$browserDomCopyPath = Copy-ArtifactIfExists -SourcePath $smokeSummary.browserDom -DestinationDirectory $assetDirectory
$jarFileName = Split-Path -Leaf $jarCopyPath
$browserSmokeStatus = if ($smokeSummary.browserSmoke -and $smokeSummary.browserSmoke.status) { [string]$smokeSummary.browserSmoke.status } else { 'not included' }
$webClaimSmokeStatus = if ($smokeSummary.webClaimSmoke -and $smokeSummary.webClaimSmoke.status) { [string]$smokeSummary.webClaimSmoke.status } else { 'not included' }
$browserScreenshotLeaf = if ($browserScreenshotCopyPath) { Split-Path -Leaf $browserScreenshotCopyPath } else { '未复制' }
$browserMobileScreenshotLeaf = if ($browserMobileScreenshotCopyPath) { Split-Path -Leaf $browserMobileScreenshotCopyPath } else { '未复制' }
$verificationSummaryLeaf = if ($verificationSummaryCopyPath) { Split-Path -Leaf $verificationSummaryCopyPath } else { '未复制' }
$releaseChannelPackLeaf = if ($releaseChannelPackCopyPath) { Split-Path -Leaf $releaseChannelPackCopyPath } else { '未复制' }
$hudContractSummaryLeaf = if ($hudContractSummaryCopyPath) { Split-Path -Leaf $hudContractSummaryCopyPath } else { '未复制' }
$performanceBudgetSummaryLeaf = if ($performanceBudgetSummaryCopyPath) { Split-Path -Leaf $performanceBudgetSummaryCopyPath } else { '未复制' }
$sparkProfileSummaryLeaf = if ($sparkProfileSummaryCopyPath) { Split-Path -Leaf $sparkProfileSummaryCopyPath } else { '未提供' }
$sparkProfileStatus = if ($sparkProfileSummary) { [string]$sparkProfileSummary.status } else { 'not included' }

$modrinthTagsNormalized = ($modrinthTags -split "`r?`n" | ForEach-Object {
    $line = $_.Trim()
    if ($line.StartsWith('-')) {
        $line = $line.Substring(1).Trim()
    }
    Unwrap-MarkdownText $line
} | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

Write-Utf8File -Path (Join-Path $modrinthDirectory 'subtitle.txt') -Content $subtitle
Write-Utf8File -Path (Join-Path $modrinthDirectory 'short-description.txt') -Content $shortDescription
Write-Utf8File -Path (Join-Path $modrinthDirectory 'long-description.md') -Content $modrinthLongDescription
Write-Utf8File -Path (Join-Path $modrinthDirectory 'tags.txt') -Content (($modrinthTagsNormalized -join [Environment]::NewLine).Trim())

Write-Utf8File -Path (Join-Path $hangarDirectory 'overview.md') -Content $hangarOverview
Write-Utf8File -Path (Join-Path $hangarDirectory 'features.md') -Content $hangarFeatures
Write-Utf8File -Path (Join-Path $hangarDirectory 'installation.md') -Content $hangarInstall

Write-Utf8File -Path (Join-Path $spigotDirectory 'summary.md') -Content $spigotSummary
Write-Utf8File -Path (Join-Path $spigotDirectory 'highlights.md') -Content $spigotHighlights
Write-Utf8File -Path (Join-Path $spigotDirectory 'recommended-servers.md') -Content $spigotServerFit

Write-Utf8File -Path (Join-Path $changelogDirectory 'first-public.txt') -Content $firstPublicChangelog
Write-Utf8File -Path (Join-Path $changelogDirectory 'maintenance.txt') -Content $maintenanceChangelog

$uploadGuideLines = @(
    '# STARCORE 发布渠道成品包',
    '',
    ('生成时间：{0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')),
    '',
    '## 当前快照',
    '',
    ('- Jar：`{0}`' -f $jarFileName),
    ('- 测试：`{0}` / failures=`{1}` / errors=`{2}` / skipped=`{3}`' -f $testSummary.tests, $testSummary.failures, $testSummary.errors, $testSummary.skipped),
    ('- Browser smoke：`{0}`' -f $browserSmokeStatus),
    ('- Web claim smoke：`{0}`' -f $webClaimSmokeStatus),
    ('- Map HUD contract：`{0}`' -f $hudContractSummary.status),
    ('- Performance budget：`{0}` ({1}/{2} pass)' -f $performanceBudgetSummary.status, $performanceBudgetSummary.totals.passed, $performanceBudgetSummary.totals.checks),
    ('- Performance trend：`{0}`' -f $(if ($performanceBudgetSummary.baseline) { $performanceBudgetSummary.baseline.status } else { 'not compared' })),
    ('- Spark profiling：`{0}`' -f $sparkProfileStatus),
    ('- Browser 截图：`{0}`' -f $browserScreenshotLeaf),
    ('- Browser 移动端截图：`{0}`' -f $browserMobileScreenshotLeaf),
    '',
    '## 平台成品文件',
    '',
    '- Modrinth',
    '  - `modrinth/subtitle.txt`',
    '  - `modrinth/short-description.txt`',
    '  - `modrinth/long-description.md`',
    '  - `modrinth/tags.txt`',
    '- Hangar',
    '  - `hangar/overview.md`',
    '  - `hangar/features.md`',
    '  - `hangar/installation.md`',
    '- SpigotMC',
    '  - `spigotmc/summary.md`',
    '  - `spigotmc/highlights.md`',
    '  - `spigotmc/recommended-servers.md`',
    '- 更新日志模板',
    '  - `changelogs/first-public.txt`',
    '  - `changelogs/maintenance.txt`',
    '',
    '## 上传时建议一起带上的材料',
    '',
    ('- 发布 jar：`package/{0}`' -f $jarFileName),
    ('- 浏览器截图：`assets/{0}`' -f $browserScreenshotLeaf),
    ('- 浏览器移动端截图：`assets/{0}`' -f $browserMobileScreenshotLeaf),
    ('- 自动验证摘要：`references/{0}`' -f $verificationSummaryLeaf),
    ('- HUD 契约摘要：`references/{0}`' -f $hudContractSummaryLeaf),
    ('- 性能预算摘要：`references/{0}`' -f $performanceBudgetSummaryLeaf),
    ('- Spark profiling 摘要：`references/{0}`' -f $sparkProfileSummaryLeaf),
    ('- 发布渠道文案总表：`references/{0}`' -f $releaseChannelPackLeaf),
    '',
    '## 一句话定位',
    '',
    $oneLinePositioning,
    '',
    '## 核心卖点',
    '',
    $coreSellingPoints
)
Write-Utf8File -Path (Join-Path $outputDirectoryPath 'UPLOAD_GUIDE.md') -Content (($uploadGuideLines -join [Environment]::NewLine).Trim())

$manifestPath = Join-Path $outputDirectoryPath 'release-channel-assets-manifest.json'
$manifest = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    projectRoot = $projectRootPath
    snapshot = [ordered]@{
        tests = $testSummary.tests
        failures = $testSummary.failures
        errors = $testSummary.errors
        skipped = $testSummary.skipped
        browserSmoke = if ($smokeSummary.browserSmoke) { $smokeSummary.browserSmoke.status } else { '' }
        webClaimSmoke = if ($smokeSummary.webClaimSmoke) { $smokeSummary.webClaimSmoke.status } else { '' }
        mapHudContract = $hudContractSummary.status
        mapHudContractSourceMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'source' } | Select-Object -First 1).matchCount } else { '' }
        mapHudContractStaticPreviewMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'static-preview' } | Select-Object -First 1).matchCount } else { '' }
        mapHudContractRuntimeMatches = if ($hudContractSummary.roots) { ($hudContractSummary.roots | Where-Object { $_.label -eq 'runtime-map' } | Select-Object -First 1).matchCount } else { '' }
        mapHudBrowserSmokeContract = if ($hudContractSummary.browserSmoke) { $hudContractSummary.browserSmoke.status } else { '' }
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
    package = [ordered]@{
        jar = $jarCopyPath
    }
    assets = [ordered]@{
        browserScreenshot = $browserScreenshotCopyPath
        browserMobileScreenshot = $browserMobileScreenshotCopyPath
        browserDom = $browserDomCopyPath
    }
    references = [ordered]@{
        verificationSummary = $verificationSummaryCopyPath
        releaseChannelPack = $releaseChannelPackCopyPath
        readme = $readmeCopyPath
        smokeSummary = $smokeSummaryCopyPath
        mapHudContractSummary = $hudContractSummaryCopyPath
        performanceBudgetSummary = $performanceBudgetSummaryCopyPath
        sparkProfileSummary = $sparkProfileSummaryCopyPath
        sparkProfileArtifact = $sparkProfileArtifactCopyPath
    }
    generatedFiles = [ordered]@{
        uploadGuide = Join-Path $outputDirectoryPath 'UPLOAD_GUIDE.md'
        modrinth = [ordered]@{
            subtitle = Join-Path $modrinthDirectory 'subtitle.txt'
            shortDescription = Join-Path $modrinthDirectory 'short-description.txt'
            longDescription = Join-Path $modrinthDirectory 'long-description.md'
            tags = Join-Path $modrinthDirectory 'tags.txt'
        }
        hangar = [ordered]@{
            overview = Join-Path $hangarDirectory 'overview.md'
            features = Join-Path $hangarDirectory 'features.md'
            installation = Join-Path $hangarDirectory 'installation.md'
        }
        spigotmc = [ordered]@{
            summary = Join-Path $spigotDirectory 'summary.md'
            highlights = Join-Path $spigotDirectory 'highlights.md'
            recommendedServers = Join-Path $spigotDirectory 'recommended-servers.md'
        }
        changelogs = [ordered]@{
            firstPublic = Join-Path $changelogDirectory 'first-public.txt'
            maintenance = Join-Path $changelogDirectory 'maintenance.txt'
        }
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
        throw "Release channel assets output directory was empty: $outputDirectoryPath"
    }
    Compress-Archive -Path $archiveInputs -DestinationPath $zipPath -CompressionLevel Optimal
}

[pscustomobject]@{
    status = 'ok'
    outputDirectory = $outputDirectoryPath
    zipPath = $zipPath
    manifestPath = $manifestPath
    uploadGuidePath = (Join-Path $outputDirectoryPath 'UPLOAD_GUIDE.md')
    modrinthLongDescriptionPath = (Join-Path $modrinthDirectory 'long-description.md')
    hangarOverviewPath = (Join-Path $hangarDirectory 'overview.md')
    spigotSummaryPath = (Join-Path $spigotDirectory 'summary.md')
} | ConvertTo-Json -Depth 6
