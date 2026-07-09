param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$ServerRoot = '',
    [string]$PaperJar = 'paper-1.21.11-69.jar',
    [string]$PaperApiJar = '',
    [string]$ProtectorApiJar = '',
    [int]$TimeoutSeconds = 360,
    [switch]$BrowserSmoke,
    [switch]$ProtectorApiSmoke,
    [string]$BrowserPath = ''
)

$ErrorActionPreference = 'Stop'
$ProtectorApiPluginVersion = '2.2.1'
$ProtectorApiPluginUrl = 'https://cdn.modrinth.com/data/yZzJLLCk/versions/qf6PMeVn/ProtectorAPI-Plugin-2.2.1.jar'

function Resolve-RequiredPath {
    param([string]$Path, [string]$Label)
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        throw "$Label not found: $Path"
    }
    return $resolved.Path
}

function Resolve-ProtectorApiJarPath {
    param(
        [string]$ExplicitPath,
        [string]$CacheDir
    )
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        return Resolve-RequiredPath $ExplicitPath 'ProtectorAPI jar'
    }
    New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null
    $downloadTarget = Join-Path $CacheDir "ProtectorAPI-Plugin-$ProtectorApiPluginVersion.jar"
    if (-not (Test-Path -LiteralPath $downloadTarget)) {
        Invoke-WebRequest -UseBasicParsing -Uri $ProtectorApiPluginUrl -OutFile $downloadTarget
    }
    return Resolve-RequiredPath $downloadTarget 'ProtectorAPI jar'
}

$ProjectRoot = Resolve-RequiredPath $ProjectRoot 'Project root'
if ([string]::IsNullOrWhiteSpace($ServerRoot)) {
    $ServerRoot = Join-Path (Split-Path $ProjectRoot -Parent) 'test-server-paper-1.21.11'
}
$ServerRoot = Resolve-RequiredPath $ServerRoot 'Paper server root'
$StarCoreJar = Join-Path $ProjectRoot 'target\starcore-0.1.0-SNAPSHOT.jar'
$StarCoreJar = Resolve-RequiredPath $StarCoreJar 'STARCORE jar'
$PaperJarPath = Join-Path $ServerRoot $PaperJar
$PaperJarPath = Resolve-RequiredPath $PaperJarPath 'Paper jar'
if ([string]::IsNullOrWhiteSpace($PaperApiJar)) {
    $paperApiCandidates = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository\io\papermc\paper\paper-api') -Recurse -Filter 'paper-api-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*1.21.11-R0.1-SNAPSHOT*' } |
        Sort-Object LastWriteTime -Descending
    if (-not $paperApiCandidates) {
        throw 'Paper API jar not found in Maven local repository. Run mvn -q test once to resolve dependencies.'
    }
    $PaperApiJar = $paperApiCandidates[0].FullName
}
$PaperApiJar = Resolve-RequiredPath $PaperApiJar 'Paper API jar'
$PluginsDir = Join-Path $ServerRoot 'plugins'
New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
$starCoreDataDir = Join-Path $PluginsDir 'STARCORE'

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$harnessRoot = Join-Path $ProjectRoot "target\smoke-harness-$stamp"
$sourceDir = Join-Path $harnessRoot 'src\dev\starcore\smoke'
$classesDir = Join-Path $harnessRoot 'classes'
$harnessJar = Join-Path $harnessRoot 'starcore-smoke-harness.jar'
$outLog = Join-Path $ServerRoot "zcode-starcore-deep-integration-smoke-$stamp.out.log"
$errLog = Join-Path $ServerRoot "zcode-starcore-deep-integration-smoke-$stamp.err.log"
$healthFile = Join-Path $ServerRoot "zcode-starcore-deep-integration-smoke-$stamp.health.txt"
$browserDomFile = Join-Path $ServerRoot "zcode-starcore-map-browser-smoke-$stamp.dom.html"
$browserScreenshotFile = Join-Path $ServerRoot "zcode-starcore-map-browser-smoke-$stamp.png"
$browserMobileScreenshotFile = Join-Path $ServerRoot "zcode-starcore-map-browser-smoke-$stamp.mobile.png"
$starCoreDataBackupDir = Join-Path $harnessRoot 'STARCORE-backup'
$starCoreDataArtifactDir = Join-Path $harnessRoot 'STARCORE-artifact'
$protectorApiJarBackupDir = Join-Path $harnessRoot 'ProtectorAPI-plugin-backup'
$protectorApiDataDir = Join-Path $PluginsDir 'ProtectorAPI'
$protectorApiDataBackupDir = Join-Path $harnessRoot 'ProtectorAPI-backup'
$protectorApiDataArtifactDir = Join-Path $harnessRoot 'ProtectorAPI-artifact'
$protectorApiCacheDir = Join-Path $ProjectRoot 'target\smoke-dependencies'
$hadProtectorApiData = Test-Path -LiteralPath $protectorApiDataDir
$hadStarCoreData = Test-Path -LiteralPath $starCoreDataDir

New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

if ($hadStarCoreData) {
    Move-Item -LiteralPath $starCoreDataDir -Destination $starCoreDataBackupDir
}
if ($ProtectorApiSmoke -and $hadProtectorApiData) {
    Move-Item -LiteralPath $protectorApiDataDir -Destination $protectorApiDataBackupDir
}

$existingProtectorApiJars = @()
$installedProtectorApiJar = ''
$resolvedProtectorApiJar = ''
if ($ProtectorApiSmoke) {
    $existingProtectorApiJars = @(Get-ChildItem -LiteralPath $PluginsDir -Filter 'ProtectorAPI*.jar' -File -ErrorAction SilentlyContinue)
    if ($existingProtectorApiJars.Count -gt 0) {
        New-Item -ItemType Directory -Path $protectorApiJarBackupDir -Force | Out-Null
        foreach ($existingJar in $existingProtectorApiJars) {
            Move-Item -LiteralPath $existingJar.FullName -Destination (Join-Path $protectorApiJarBackupDir $existingJar.Name)
        }
    }
    $resolvedProtectorApiJar = Resolve-ProtectorApiJarPath -ExplicitPath $ProtectorApiJar -CacheDir $protectorApiCacheDir
}

function Resolve-BrowserExecutable {
    param([string]$ExplicitPath)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        return Resolve-RequiredPath $ExplicitPath 'Browser executable'
    }
    $candidates = @(
        'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
        'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
        'C:\Program Files\Google\Chrome\Application\chrome.exe',
        'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    throw 'No headless browser executable found. Install Microsoft Edge or Chrome, or pass -BrowserPath.'
}

function Read-SmokeResultProperties {
    param([string]$Path)
    $resolved = Resolve-RequiredPath $Path 'Smoke result properties'
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $resolved -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 0) {
            continue
        }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $properties[$key] = $value
        }
    }
    return $properties
}

function Assert-SmokeSqliteCounts {
    param(
        [hashtable]$ResultProperties
    )
    if ($null -eq $ResultProperties -or $ResultProperties.Count -eq 0) {
        throw 'Smoke sqlite assertions could not run because result properties were empty.'
    }

    $requiredPositiveTables = @(
        'starcore_nation_state',
        'starcore_event_state',
        'starcore_officer_state',
        'starcore_policy_state',
        'starcore_resource_state',
        'starcore_resolution_state'
    )

    foreach ($table in $requiredPositiveTables) {
        $key = "sqlite.$table"
        if (-not $ResultProperties.ContainsKey($key)) {
            throw "Smoke sqlite assertions missing result property: $key"
        }
        $value = 0
        if (-not [int]::TryParse([string]$ResultProperties[$key], [ref]$value)) {
            throw "Smoke sqlite assertions could not parse integer for ${key}: $($ResultProperties[$key])"
        }
        if ($value -le 0) {
            throw "Smoke sqlite assertion failed: $table expected > 0 but was $value"
        }
    }
}

function New-SmokeSummary {
    param(
        [hashtable]$ResultProperties,
        [string]$Health,
        [string]$Marker,
        [string]$MarkerLine,
        [string]$OutLog,
        [string]$ErrLog,
        [string]$HealthFile,
        [string]$HarnessJar,
        [string]$ProtectorApiSmoke,
        [string]$ProtectorApiJar,
        [object]$WebClaimSmokeResult,
        [object]$BrowserSmokeResult,
        [string]$BrowserDom,
        [string]$BrowserScreenshot,
        [string]$BrowserMobileScreenshot,
        [string]$StarCoreDataArtifact,
        [string]$ProtectorApiDataArtifact,
        [object]$StarCoreDataRestored,
        [object]$ProtectorApiDataRestored,
        [object]$JavaProcessExited,
        [string]$PortListen,
        [string]$ErrorMatches
    )
    $sqliteCounts = [ordered]@{}
    if ($null -ne $ResultProperties) {
        foreach ($entry in $ResultProperties.GetEnumerator() | Sort-Object Name) {
            if ($entry.Key -like 'sqlite.*') {
                $sqliteCounts[$entry.Key.Substring(7)] = [int]$entry.Value
            }
        }
    }

    return [ordered]@{
        health = $Health
        marker = $Marker
        markerLine = $MarkerLine
        outLog = $OutLog
        errLog = $ErrLog
        healthFile = $HealthFile
        harnessJar = $HarnessJar
        protectorApiSmoke = $ProtectorApiSmoke
        protectorApiJar = $ProtectorApiJar
        webClaimSmoke = if ($WebClaimSmokeResult) { [ordered]@{ status = $WebClaimSmokeResult.Status; details = $WebClaimSmokeResult.Details } } else { $null }
        browserSmoke = if ($BrowserSmokeResult) { [ordered]@{ status = $BrowserSmokeResult.Status; details = $BrowserSmokeResult.Details; domFile = $BrowserSmokeResult.DomFile; screenshotFile = $BrowserSmokeResult.ScreenshotFile; mobileScreenshotFile = $BrowserSmokeResult.MobileScreenshotFile; browser = $BrowserSmokeResult.Browser } } else { $null }
        browserDom = $BrowserDom
        browserScreenshot = $BrowserScreenshot
        browserMobileScreenshot = $BrowserMobileScreenshot
        starCoreDataArtifact = $StarCoreDataArtifact
        protectorApiDataArtifact = $ProtectorApiDataArtifact
        starCoreDataRestored = $StarCoreDataRestored
        protectorApiDataRestored = $ProtectorApiDataRestored
        javaProcessExited = $JavaProcessExited
        portListen = $PortListen
        errorMatches = $ErrorMatches
        harness = if ($null -ne $ResultProperties) { [ordered]@{
            status = $ResultProperties['status']
            message = $ResultProperties['message']
            browserUrl = $ResultProperties['browser_url']
            viewerName = $ResultProperties['viewer_name']
            viewerNation = $ResultProperties['viewer_nation']
            viewerBalance = $ResultProperties['viewer_balance']
            viewerGovernment = $ResultProperties['viewer_government']
            viewerRole = $ResultProperties['viewer_role']
            viewerFounder = $ResultProperties['viewer_founder']
            viewerExperience = $ResultProperties['viewer_experience']
            viewerClaims = $ResultProperties['viewer_claims']
            viewerCityStates = $ResultProperties['viewer_city_states']
            viewerResources = $ResultProperties['viewer_resources']
            viewerProgress = $ResultProperties['viewer_progress']
            viewerNextLevel = $ResultProperties['viewer_next_level']
            viewerOnline = $ResultProperties['viewer_online']
            sqliteCounts = $sqliteCounts
        } } else { $null }
    }
}

function Assert-DomContains {
    param(
        [string]$Dom,
        [string]$Expected,
        [string]$Label
    )
    if ($Dom -notlike "*$Expected*") {
        throw "Browser smoke did not render ${Label}: $Expected"
    }
}

function ConvertTo-CmdArgument {
    param([string]$Value)
    return '"' + ($Value -replace '"', '\"') + '"'
}

function Invoke-BrowserCommand {
    param(
        [string]$BrowserExe,
        [string[]]$Arguments
    )
    $commandLine = (ConvertTo-CmdArgument $BrowserExe) + ' ' + (($Arguments | ForEach-Object { ConvertTo-CmdArgument $_ }) -join ' ')
    return & cmd.exe /d /s /c $commandLine 2>&1
}

function Invoke-StarCoreProtectedClaimApiSmoke {
    param(
        [string]$BrowserUrl,
        [string]$ProtectorSummary
    )
    if ([string]::IsNullOrWhiteSpace($BrowserUrl)) {
        throw 'Web claim smoke browser URL was empty.'
    }
    if ([string]::IsNullOrWhiteSpace($ProtectorSummary)) {
        throw 'Web claim smoke protector summary was empty.'
    }
    if ($ProtectorSummary -notmatch '^runtime:(?<provider>[^@]+)@(?<chunkX>-?\d+):(?<chunkZ>-?\d+)$') {
        throw "Could not parse protector summary from smoke marker: $ProtectorSummary"
    }

    $providerName = $Matches['provider']
    $chunkX = [int]$Matches['chunkX']
    $chunkZ = [int]$Matches['chunkZ']
    $minX = $chunkX * 16
    $maxX = (($chunkX + 2) * 16) - 1
    $minZ = $chunkZ * 16
    $maxZ = (($chunkZ + 2) * 16) - 1

    $viewerUri = [System.Uri]$BrowserUrl
    $query = if ([string]::IsNullOrWhiteSpace($viewerUri.Query)) { '' } else { $viewerUri.Query.TrimStart('?') + '&' }
    $query += "world=world&minX=$minX&maxX=$maxX&minZ=$minZ&maxZ=$maxZ"

    $previewBuilder = [System.UriBuilder]::new($viewerUri)
    $previewBuilder.Path = '/api/map/claim/preview'
    $previewBuilder.Query = $query
    $previewResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $previewBuilder.Uri.AbsoluteUri -ContentType 'application/x-www-form-urlencoded; charset=UTF-8' -Body '' -TimeoutSec 15
    if ($previewResponse.StatusCode -ne 200) {
        throw "Web claim preview returned unexpected status $($previewResponse.StatusCode): $($previewResponse.Content)"
    }
    $previewJson = $previewResponse.Content | ConvertFrom-Json
    if ($previewJson.canSubmit -ne $false) {
        throw "Web claim preview unexpectedly allowed submission: $($previewResponse.Content)"
    }
    if ($previewJson.message -notlike '*外部保护区域冲突*') {
        throw "Web claim preview did not mention external protection conflict: $($previewResponse.Content)"
    }
    if ($previewJson.explanation.state -ne 'external-protection-conflict') {
        throw "Web claim preview did not expose external-protection explanation state: $($previewResponse.Content)"
    }
    if (-not (($previewJson.explanation.reasons | ForEach-Object { $_.code }) -contains 'external-protection-conflict')) {
        throw "Web claim preview did not expose external-protection explanation reason: $($previewResponse.Content)"
    }
    if ($previewJson.message -notlike "*$providerName*") {
        throw "Web claim preview did not include synthetic provider: $($previewResponse.Content)"
    }

    $requestBuilder = [System.UriBuilder]::new($viewerUri)
    $requestBuilder.Path = '/api/map/claim/request'
    $requestBuilder.Query = $query
    $requestResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $requestBuilder.Uri.AbsoluteUri -ContentType 'application/x-www-form-urlencoded; charset=UTF-8' -Body '' -TimeoutSec 15
    if ($requestResponse.StatusCode -ne 200) {
        throw "Web claim request returned unexpected status $($requestResponse.StatusCode): $($requestResponse.Content)"
    }
    $requestJson = $requestResponse.Content | ConvertFrom-Json
    if ($requestJson.canSubmit -ne $false) {
        throw "Web claim request unexpectedly allowed submission: $($requestResponse.Content)"
    }
    if ($requestJson.requestSubmitted -ne $false) {
        throw "Web claim request unexpectedly created a pending request: $($requestResponse.Content)"
    }
    if ($requestJson.message -notlike '*外部保护区域冲突*') {
        throw "Web claim request did not mention external protection conflict: $($requestResponse.Content)"
    }
    if ($requestJson.explanation.state -ne 'external-protection-conflict') {
        throw "Web claim request did not expose external-protection explanation state: $($requestResponse.Content)"
    }
    if (-not (($requestJson.explanation.reasons | ForEach-Object { $_.code }) -contains 'external-protection-conflict')) {
        throw "Web claim request did not expose external-protection explanation reason: $($requestResponse.Content)"
    }
    if ($requestJson.message -notlike "*$providerName*") {
        throw "Web claim request did not include synthetic provider: $($requestResponse.Content)"
    }

    return [pscustomobject]@{
        Status = 'PASS'
        Details = "provider=$providerName chunk=$chunkX,$chunkZ bounds=$minX,$minZ->$maxX,$maxZ"
    }
}

function Invoke-StarCoreMapBrowserSmoke {
    param(
        [string]$BrowserUrl,
        [string]$ViewerName,
        [string]$ViewerNation,
        [string]$ViewerBalance,
        [string]$ViewerGovernment,
        [string]$ViewerRole,
        [string]$ViewerFounder,
        [string]$ViewerExperience,
        [string]$ViewerClaims,
        [string]$ViewerCityStates,
        [string]$ViewerResources,
        [string]$ViewerProgress,
        [string]$ViewerNextLevel,
        [string]$ViewerOnline,
        [string]$ResourceRuntimeLowUrl,
        [string]$ResourceRuntimeOfflineUrl,
        [string]$DomFile,
        [string]$ScreenshotFile,
        [string]$MobileScreenshotFile,
        [string]$HarnessRoot,
        [string]$ExplicitBrowserPath,
        [string]$BrowserScript
    )
    if ([string]::IsNullOrWhiteSpace($BrowserUrl)) {
        throw 'Browser smoke URL was empty.'
    }
    $terrainSeparator = if ($BrowserUrl.Contains('?')) { '&' } else { '?' }
    $BrowserUrl = "${BrowserUrl}${terrainSeparator}terrain=off"
    $browserExe = Resolve-BrowserExecutable $ExplicitBrowserPath
    $profileDir = Join-Path $HarnessRoot 'browser-profile'
    $BrowserScript = Resolve-RequiredPath $BrowserScript 'Browser smoke script'
    $nodeArgs = @(
        $BrowserScript,
        '--url', $BrowserUrl,
        '--viewer-name', $ViewerName,
        '--viewer-nation', $ViewerNation,
        '--viewer-balance', $ViewerBalance,
        '--viewer-government', $ViewerGovernment,
        '--viewer-role', $ViewerRole,
        '--viewer-founder', $ViewerFounder,
        '--viewer-claims', $ViewerClaims,
        '--viewer-city-states', $ViewerCityStates,
        '--viewer-resources', $ViewerResources,
        '--viewer-progress', $ViewerProgress,
        '--viewer-next-level', $ViewerNextLevel,
        '--viewer-online', $ViewerOnline,
        '--resource-runtime-low-url', $ResourceRuntimeLowUrl,
        '--resource-runtime-offline-url', $ResourceRuntimeOfflineUrl,
        '--browser', $browserExe,
        '--dom', $DomFile,
        '--screenshot', $ScreenshotFile,
        '--mobile-screenshot', $MobileScreenshotFile,
        '--profile', $profileDir,
        '--timeout-ms', '45000'
    )
    $nodeOutput = & node @nodeArgs 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Browser smoke failed with exit code $exitCode`n$($nodeOutput -join [Environment]::NewLine)"
    }
    $dom = Get-Content -LiteralPath $DomFile -Raw -Encoding UTF8
    if (($dom -notlike '*访问者*') -and ($dom -notlike '*Viewer*')) {
        throw 'Browser smoke did not render the viewer intel card label.'
    }
    Assert-DomContains $dom $ViewerName 'viewer name'
    Assert-DomContains $dom $ViewerNation 'viewer nation'
    Assert-DomContains $dom $ViewerBalance 'viewer balance'
    if (-not [string]::IsNullOrWhiteSpace($ViewerGovernment)) {
        Assert-DomContains $dom $ViewerGovernment 'viewer government'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerRole)) {
        Assert-DomContains $dom $ViewerRole 'viewer role'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerFounder)) {
        Assert-DomContains $dom $ViewerFounder 'viewer founder'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerExperience)) {
        Assert-DomContains $dom $ViewerExperience 'viewer experience'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerClaims)) {
        Assert-DomContains $dom $ViewerClaims 'viewer claim limit'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerCityStates)) {
        Assert-DomContains $dom $ViewerCityStates 'viewer city-state limit'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerResources)) {
        Assert-DomContains $dom $ViewerResources 'viewer resource district limit'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerProgress)) {
        Assert-DomContains $dom $ViewerProgress 'viewer level progress'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerNextLevel)) {
        Assert-DomContains $dom $ViewerNextLevel 'viewer next-level remaining'
    }
    if (-not [string]::IsNullOrWhiteSpace($ViewerOnline)) {
        Assert-DomContains $dom $ViewerOnline 'viewer online state'
    }
    Assert-DomContains $dom 'data-officer-authorization="true"' 'officer authorization block'
    Assert-DomContains $dom 'marshal' 'marshal officer authorization'
    Assert-DomContains $dom 'treasurer' 'treasurer officer authorization'
    Assert-DomContains $dom 'diplomat' 'diplomat officer authorization'
    Assert-DomContains $dom 'steward' 'steward officer authorization'
    Assert-DomContains $dom 'data-officer-authorization-can="true"' 'officer authorization access state'
    Assert-DomContains $dom 'data-officer-authorization-status="founder"' 'founder officer authorization state'
    Assert-DomContains $dom 'data-event-ledger-search="query"' 'event ledger search input'

    $escapedBalance = [regex]::Escape($ViewerBalance)
    if ($dom -notmatch "id=`"claim-balance`"[^>]*>\s*$escapedBalance\s*</strong>") {
        throw "Browser smoke did not render pre-selection claim balance $ViewerBalance in #claim-balance."
    }
    if (-not (Test-Path -LiteralPath $ScreenshotFile)) {
        throw "Browser screenshot was not created: $ScreenshotFile"
    }
    if (-not (Test-Path -LiteralPath $MobileScreenshotFile)) {
        throw "Browser mobile screenshot was not created: $MobileScreenshotFile"
    }
    $browserMarker = (($nodeOutput | Where-Object { $_ -like 'STARCORE_BROWSER_SMOKE_PASS*' }) -join ' ')
    if ($browserMarker -notmatch 'officerAuth=marshal\+treasurer\+diplomat\+steward:9') {
        throw "Browser smoke marker did not prove officer authorization matrix: $browserMarker"
    }
    if ($browserMarker -notmatch 'officerAccess=founder:9') {
        throw "Browser smoke marker did not prove viewer officer authorization access state: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventSearch=[^:]+:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger search: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventContext=[a-z]+-[^:]+:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger context chip search: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventReason=reason-[^:]+:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger reason chip search: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventJump=reason-[^:]+:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger cross-event jump: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventOps=resource\+explanation\+auth\+group:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger operation bridge: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventOpsFamilies=finance\+war\+officer\+diplomacy\+strategy\+territory\+nation:7') {
        throw "Browser smoke marker did not prove non-resource event operation bridges: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventMobile=390x844:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove mobile event ledger baseline: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventFacts=amount\+balance:[1-9][0-9]*') {
        throw "Browser smoke marker did not prove event ledger read-only context facts: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventFamilies=finance\+war\+officer\+diplomacy\+strategy\+territory\+nation:7') {
        throw "Browser smoke marker did not prove multi-family event ledger contexts: $browserMarker"
    }
    if ($browserMarker -notmatch 'eventFamilyMobile=finance\+war\+officer\+diplomacy\+strategy\+territory\+nation:7') {
        throw "Browser smoke marker did not prove multi-family mobile event ledger baseline: $browserMarker"
    }
    if ($browserMarker -notmatch 'resourceExplanationFixture=ready\+awaiting-target\+waiting-depletion\+insufficient-balance\+player-offline:5') {
        throw "Browser smoke marker did not prove resource explanation multi-state fixture: $browserMarker"
    }
    if ($browserMarker -notmatch 'resourceExplanationRuntime=ready\+awaiting-target\+waiting-depletion\+insufficient-balance\+player-offline:5') {
        throw "Browser smoke marker did not prove real runtime resource explanation states: $browserMarker"
    }
    return [pscustomobject]@{
        Status = 'PASS'
        Browser = $browserExe
        DomFile = $DomFile
        ScreenshotFile = $ScreenshotFile
        MobileScreenshotFile = $MobileScreenshotFile
        Details = $browserMarker
    }
}

$javaSource = @'
package dev.starcore.smoke;

import dev.starcore.starcore.api.StarCoreApi;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.map.MapService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictSnapshot;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.resolution.ResolutionService;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionState;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.sqlite.SQLiteDataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;

public final class StarCoreSmokeHarnessPlugin extends JavaPlugin {
    private static final boolean PROTECTOR_API_SMOKE = __PROTECTOR_API_SMOKE__;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::runSmoke, 80L);
    }

    private void runSmoke() {
        try {
            StarCoreApi api = Bukkit.getServicesManager().load(StarCoreApi.class);
            require(api != null, "STARCORE public API is not registered");
            NationService nations = api.nationService().orElseThrow(() -> new IllegalStateException("NationService missing"));
            InternalEconomyService economy = api.service(InternalEconomyService.class).orElseThrow(() -> new IllegalStateException("InternalEconomyService missing"));
            ClaimToolService claimTool = api.claimToolService().orElseThrow(() -> new IllegalStateException("ClaimToolService missing"));
            NationResourceDistrictService districts = api.service(NationResourceDistrictService.class).orElseThrow(() -> new IllegalStateException("NationResourceDistrictService missing"));
            MapService map = api.mapService().orElseThrow(() -> new IllegalStateException("MapService missing"));
            TreasuryService treasury = api.treasuryService().orElseThrow(() -> new IllegalStateException("TreasuryService missing"));
            DiplomacyService diplomacy = api.diplomacyService().orElseThrow(() -> new IllegalStateException("DiplomacyService missing"));
            PolicyService policy = api.policyService().orElseThrow(() -> new IllegalStateException("PolicyService missing"));
            ResourceService resource = api.resourceService().orElseThrow(() -> new IllegalStateException("ResourceService missing"));
            ResolutionService resolution = api.resolutionService().orElseThrow(() -> new IllegalStateException("ResolutionService missing"));
            TechnologyService technology = api.technologyService().orElseThrow(() -> new IllegalStateException("TechnologyService missing"));
            WarService war = api.warService().orElseThrow(() -> new IllegalStateException("WarService missing"));
            OfficerService officer = api.officerService().orElseThrow(() -> new IllegalStateException("OfficerService missing"));
            EventService event = api.service(EventService.class).orElseThrow(() -> new IllegalStateException("EventService missing"));
            ExternalProtectionService externalProtection = api.service(ExternalProtectionService.class).orElse(null);

            UUID playerId = UUID.randomUUID();
            String playerName = "SmokePlayer";
            economy.deposit(playerId, new BigDecimal("10000000.00"));

            String nationName = "Smoke" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
            Nation nation = nations.createNation(playerId, playerName, nationName);
            UUID secondPlayerId = UUID.randomUUID();
            String secondPlayerName = "SmokeEnvoy";
            economy.deposit(secondPlayerId, new BigDecimal("10000000.00"));
            Nation secondNation = nations.createNation(secondPlayerId, secondPlayerName, nationName + "b");
            UUID offlinePlayerId = UUID.randomUUID();
            String offlinePlayerName = "SmokeOffline";
            economy.deposit(offlinePlayerId, new BigDecimal("10000000.00"));
            Nation offlineNation = nations.createNation(offlinePlayerId, offlinePlayerName, nationName + "c");
            String nationOperationFeedback = runNationOperationCommandFeedbackSmoke(economy, nations, resolution, event);
            String strategyFeedback = runStrategyCommandFeedbackSmoke(economy, nations, treasury, policy, technology, war, officer, event);
            String governanceFeedback = runGovernanceCommandFeedbackSmoke(economy, nations, treasury, diplomacy, officer, event, resolution);
            int baseChunk = 16 + (int) Math.floorMod(System.currentTimeMillis() / 1000L, 256L);
            ChunkClaimSelection selection = new ChunkClaimSelection("world", baseChunk, baseChunk + 1, baseChunk, baseChunk + 1);
            String protectorSmoke = PROTECTOR_API_SMOKE
                ? runProtectorApiSmoke(nations, externalProtection, map, playerId, baseChunk)
                : "disabled";
            String claimExplanationSmoke = PROTECTOR_API_SMOKE ? "externalProtection:1" : "disabled";

            ClaimSelectionPreview preview = nations.previewClaimSelection(playerId, selection);
            require(preview.canSubmit(), "claim preview rejected: " + preview.message());
            require(preview.pricing().chunks().size() == 4, "expected 4 priced chunks, got " + preview.pricing().chunks().size());
            require(preview.balance().compareTo(preview.price()) >= 0, "preview balance is below price");

            ItemStack claimToolItem = claimTool.createTool();
            require(claimToolItem != null, "claim tool item was null");
            require(claimTool.isClaimTool(claimToolItem), "claim tool marker was not recognized");

            ClaimSelectionResult result = nations.claimSelection(playerId, selection);
            require(result.claimedChunks() == 4, "expected 4 claimed chunks, got " + result.claimedChunks());
            require(nations.claimCount(nation.id()) >= 4, "nation claim count did not update");

            districts.ensureDistricts(nation);
            Collection<NationResourceDistrictSnapshot> districtSnapshots = districts.districtsOf(nation.id());
            require(!districtSnapshots.isEmpty(), "no resource district was created for claimed nation");
            NationResourceDistrictSnapshot firstDistrict = districtSnapshots.iterator().next();
            require(firstDistrict.beaconY() > 0, "resource district beacon Y was not recorded");
            World world = Bukkit.getWorld(firstDistrict.coordinate().world());
            require(world != null, "resource district world is not loaded: " + firstDistrict.coordinate().world());
            Material beaconType = world.getBlockAt(firstDistrict.beaconX(), firstDistrict.beaconY(), firstDistrict.beaconZ()).getType();
            require(!beaconType.isAir(), "resource district beacon block is air");
            BigDecimal treasuryBaseline = treasury.balance(nation.id());
            treasury.deposit(nation.id(), new BigDecimal("2500.75"));
            require(treasury.withdraw(nation.id(), new BigDecimal("100.25")), "treasury withdraw should succeed for smoke nation");
            BigDecimal expectedTreasuryBalance = treasuryBaseline.add(new BigDecimal("2400.50"));
            BigDecimal actualTreasuryBalance = treasury.balance(nation.id());
            require(expectedTreasuryBalance.compareTo(actualTreasuryBalance) == 0,
                "unexpected treasury balance: expected " + expectedTreasuryBalance + ", actual " + actualTreasuryBalance);
            diplomacy.setRelation(nation.id(), secondNation.id(), DiplomacyRelation.FRIENDLY);
            require(diplomacy.relationBetween(nation.id(), secondNation.id()) == DiplomacyRelation.FRIENDLY, "diplomacy relation did not persist in memory");
            require(policy.activatePolicy(nation.id(), "civil_industry", treasury).successful(), "policy activation should succeed for smoke nation");
            require(policy.activePolicy(nation.id()).filter("civil_industry"::equals).isPresent(), "policy activation did not persist in memory");
            require(resource.grant(nation.id(), "food", 180L), "resource grant food should succeed for smoke nation");
            require(resource.grant(nation.id(), "ore", 64L), "resource grant ore should succeed for smoke nation");
            require(resource.consume(nation.id(), "food", 30L), "resource consume food should succeed for smoke nation");
            require(resource.amount(nation.id(), "food") == 150L, "unexpected food stockpile after smoke writes: " + resource.amount(nation.id(), "food"));
            require(resource.amount(nation.id(), "ore") == 64L, "unexpected ore stockpile after smoke writes: " + resource.amount(nation.id(), "ore"));
            UUID officerPlayerId = UUID.randomUUID();
            String officerPlayerName = "SmokeMarshal";
            economy.deposit(officerPlayerId, new BigDecimal("10000000.00"));
            var joinResolution = resolution.proposeJoin(nation, playerId, playerName, officerPlayerId, officerPlayerName);
            require(resolution.find(joinResolution.id()).isPresent(), "join resolution was not registered");
            require(resolution.sign(playerId, joinResolution.id()), "join resolution signature should succeed for smoke founder");
            var enactedResolution = resolution.find(joinResolution.id()).orElseThrow(() -> new IllegalStateException("join resolution disappeared after signature"));
            require(enactedResolution.state() == ResolutionState.ENACTED, "join resolution was not enacted: " + enactedResolution.state());
            require(nations.nationOf(officerPlayerId).filter(foundNation -> foundNation.id().equals(nation.id())).isPresent(),
                "join resolution did not add applicant to nation membership");
            require(technology.unlock(nation.id(), "logistics"), "technology unlock should succeed for smoke nation");
            require(technology.hasTechnology(nation.id(), "logistics"), "technology unlock did not persist in memory");
            require(war.declareWar(nation.id(), secondNation.id()), "war declaration should succeed for smoke nations");
            require(war.atWar(nation.id(), secondNation.id()), "war declaration did not persist in memory");
            require(officer.appoint(nation.id(), "marshal", officerPlayerId, officerPlayerName), "officer appointment should succeed for smoke nation");
            require(officer.officer(nation.id(), "marshal").filter(appointment ->
                appointment.playerId().equals(officerPlayerId) && appointment.playerName().equals(officerPlayerName)
            ).isPresent(), "officer appointment did not persist in memory");
            var eventRecord = event.record(nation.id(), "resource", "Opened smoke resource district");
            var financeContextEventRecord = event.record(nation.id(), "treasury.withdraw", "Smoke finance context audit", "actor=SmokeTreasurer;reason=audit-payout;target=SmokeVault;amount=25.00;balance=475.00");
            var warContextEventRecord = event.record(nation.id(), "war.declared", "Smoke war context audit", "actor=SmokeMarshal;reason=border-conflict;warId=war-smoke;target=" + secondNation.name() + ";amount=0.00;balance=475.00");
            var officerContextEventRecord = event.record(nation.id(), "officer.appointed", "Smoke officer context audit", "actor=SmokeFounder;reason=staff-rotation;target=SmokeMarshal;member=SmokeMarshal;amount=0.00;balance=475.00");
            var diplomacyContextEventRecord = event.record(nation.id(), "diplomacy.updated", "Smoke diplomacy context audit", "actor=SmokeDiplomat;reason=treaty-reset;relation=ALLY;target=" + secondNation.name() + ";fixed=true;members=2");
            var strategyContextEventRecord = event.record(nation.id(), "policy.set", "Smoke strategy context audit", "actor=SmokeSteward;reason=strategy-review;policy=civil_industry;target=" + nation.name() + ";fixed=true;percent=15");
            var territoryContextEventRecord = event.record(nation.id(), "territory.claimed", "Smoke territory context audit", "actor=SmokeFounder;reason=border-growth;target=world:" + baseChunk + ":" + baseChunk + ";claims=" + nations.claimCount(nation.id()) + ";fixed=true");
            var nationContextEventRecord = event.record(nation.id(), "nation.created", "Smoke nation context audit", "actor=SmokeFounder;reason=foundation-cycle;target=" + nation.name() + ";members=2;claims=" + nations.claimCount(nation.id()));
            var contextEventRecord = event.record(nation.id(), "officer.audit", "Smoke browser context audit", "actor=SmokeAuditor;reason=browser-context-chip;policy=civil_industry;target=SmokeMarshal;amount=12.34;balance=56.78");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(eventRecord.id())), "event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(financeContextEventRecord.id())), "finance context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(warContextEventRecord.id())), "war context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(officerContextEventRecord.id())), "officer context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(diplomacyContextEventRecord.id())), "diplomacy context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(strategyContextEventRecord.id())), "strategy context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(territoryContextEventRecord.id())), "territory context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(nationContextEventRecord.id())), "nation context event record did not persist in memory");
            require(event.eventsOf(nation.id()).stream().anyMatch(record -> record.id().equals(contextEventRecord.id())), "context event record did not persist in memory");
            String eventFamilySmoke = "finance+war+officer+diplomacy+strategy+territory+nation:7";
            MigrationSmokeResult migrationSmoke = runMigrationSmoke(economy, districts, nation, firstDistrict, selection, officerPlayerId, officerPlayerName);
            int preWebClaimCount = nations.claimCount(nation.id());
            ChunkClaimSelection webClaimSelection = new ChunkClaimSelection("world", baseChunk + 2, baseChunk + 2, baseChunk, baseChunk);
            ClaimSelectionPreview webClaimPreview = nations.previewClaimSelection(playerId, webClaimSelection);
            require(webClaimPreview.canSubmit(), "web claim success preview rejected: " + webClaimPreview.message());

            beginSuccessfulWebClaimSmoke(map, playerId, webClaimSelection).whenComplete((requestResult, throwable) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        if (throwable != null) {
                            throw unwrapAsyncFailure(throwable);
                        }
                        PluginCommand starcore = Bukkit.getPluginCommand("starcore");
                        require(starcore != null, "starcore command is not registered");
                        List<String> confirmMessages = new ArrayList<>();
                        FeedbackCounters webConfirmFeedback = new FeedbackCounters();
                        Player confirmPlayer = commandPlayerProxy(playerId, playerName, "world", confirmMessages, webConfirmFeedback);
                        boolean handled = starcore.execute(confirmPlayer, "starcore", new String[] { "map", "confirm", requestResult.pendingId() });
                        require(handled, "starcore map confirm command returned false");
                        String confirmOutput = String.join("\n", confirmMessages);
                        require(confirmOutput.contains("网页圈地成功"), "web claim confirm output did not report success: " + confirmOutput);
                        require(webConfirmFeedback.soundCalls.get() >= 1, "web claim confirm feedback sound was not observed: " + webConfirmFeedback);
                        require(webConfirmFeedback.actionBarCalls.get() >= 1, "web claim confirm feedback actionbar was not observed: " + webConfirmFeedback);
                        require(webConfirmFeedback.bossBarCalls.get() >= 1, "web claim confirm feedback bossbar was not observed: " + webConfirmFeedback);

                        int finalClaimCount = nations.claimCount(nation.id());
                        require(finalClaimCount == preWebClaimCount + webClaimSelection.chunkCount(),
                            "web claim confirm did not increase claim count: " + finalClaimCount + " vs " + (preWebClaimCount + webClaimSelection.chunkCount()));

                        String mapSummary = map.summary();
                        require(mapSummary.contains(finalClaimCount + " territory polygon(s)"),
                            "map summary did not refresh after web claim confirm: " + mapSummary);

                        AuthenticatedMap authenticatedMap = authenticatedMap(map, playerId);
                        String viewerSnapshot = authenticatedMap.snapshot();
                        require(viewerSnapshot.contains("\"viewer\":{"), "authenticated snapshot did not include viewer object");
                        require(viewerSnapshot.contains("\"playerName\":\"" + playerName + "\""), "authenticated snapshot did not include viewer playerName");
                        require(viewerSnapshot.contains("\"nationName\":\"" + nation.name() + "\""), "authenticated snapshot did not include viewer nationName");
                        require(viewerSnapshot.contains("\"balance\":\""), "authenticated snapshot did not include viewer balance");
                        require(viewerSnapshot.contains("\"government\":\"" + nation.governmentType().name() + "\""), "authenticated snapshot did not include viewer government");
                        require(viewerSnapshot.contains("\"role\":\"founder\""), "authenticated snapshot did not include founder role");
                        require(viewerSnapshot.contains("\"founderName\":\"" + playerName + "\""), "authenticated snapshot did not include founderName");
                        require(viewerSnapshot.contains("\"online\":false"), "authenticated snapshot did not include offline viewer state");
                        require(viewerSnapshot.contains("\"nationExperience\":" + nations.experienceOf(nation.id())), "authenticated snapshot did not include viewer nationExperience");
                        require(viewerSnapshot.contains("\"nationExperienceProgress\":"), "authenticated snapshot did not include viewer nationExperienceProgress");
                        require(viewerSnapshot.contains("\"nationNextLevelExperience\":"), "authenticated snapshot did not include viewer nationNextLevelExperience");
                        require(viewerSnapshot.contains("\"nationExperienceRemaining\":"), "authenticated snapshot did not include viewer nationExperienceRemaining");
                        require(viewerSnapshot.contains("\"nationMaxLevelReached\":"), "authenticated snapshot did not include viewer nationMaxLevelReached");
                        require(viewerSnapshot.contains("\"claimCount\":" + finalClaimCount), "authenticated snapshot did not refresh viewer claimCount: " + viewerSnapshot);
                        require(viewerSnapshot.contains("\"claimLimit\":"), "authenticated snapshot did not include viewer claimLimit");
                        require(viewerSnapshot.contains("\"cityStateCount\":"), "authenticated snapshot did not include viewer cityStateCount");
                        require(viewerSnapshot.contains("\"cityStateLimit\":"), "authenticated snapshot did not include viewer cityStateLimit");
                        require(viewerSnapshot.contains("\"resourceDistrictCount\":"), "authenticated snapshot did not include viewer resourceDistrictCount");
                        require(viewerSnapshot.contains("\"resourceDistrictLimit\":"), "authenticated snapshot did not include viewer resourceDistrictLimit");
                        String viewerFounder = extractJsonString(viewerSnapshot, "founderName");
                        require(viewerFounder != null && !viewerFounder.isBlank(), "authenticated snapshot founderName could not be extracted");
                        long viewerProgressCurrent = extractJsonLong(viewerSnapshot, "nationExperienceProgress", 0L);
                        long viewerProgressTotal = extractJsonLong(viewerSnapshot, "nationNextLevelExperience", 0L);
                        long viewerNextLevelRemaining = extractJsonLong(viewerSnapshot, "nationExperienceRemaining", 0L);
                        boolean viewerMaxLevelReached = extractJsonBoolean(viewerSnapshot, "nationMaxLevelReached", false);
                        long viewerCityStateCount = extractJsonLong(viewerSnapshot, "cityStateCount", 0L);
                        long viewerCityStateLimit = extractJsonLong(viewerSnapshot, "cityStateLimit", 0L);
                        String viewerProgress = viewerMaxLevelReached ? "已满级" : viewerProgressCurrent + "/" + viewerProgressTotal;
                        String viewerNextLevel = viewerMaxLevelReached ? "已满级" : String.valueOf(viewerNextLevelRemaining);
                        String viewerCityStates = viewerCityStateCount + "/" + viewerCityStateLimit;

                        beginOnlineViewerWebClaimSmoke(
                            map,
                            secondPlayerId,
                            secondPlayerName,
                            secondNation.name(),
                            new ChunkClaimSelection("world", baseChunk + 4, baseChunk + 4, baseChunk + 4, baseChunk + 4)
                        ).whenComplete((onlineViewerSmoke, onlineThrowable) ->
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                try {
                                    if (onlineThrowable != null) {
                                        throw unwrapAsyncFailure(onlineThrowable);
                                    }
                                    ResourceExplanationRuntimeSmoke resourceRuntimeSmoke = installResourceExplanationRuntimeSmoke(
                                        economy,
                                        map,
                                        districts,
                                        nation,
                                        offlineNation,
                                        playerId,
                                        playerName,
                                        officerPlayerId,
                                        officerPlayerName,
                                        offlinePlayerId,
                                        offlinePlayerName,
                                        "world",
                                        baseChunk + 12
                                    );
                                    String migrationSummary = migrationSmoke.summary();
                                    String message = "nation=" + nation.name()
                                        + " claims=" + finalClaimCount
                                        + " price=" + result.price().toPlainString()
                                        + " districts=" + districtSnapshots.size()
                                        + " claimTool=" + claimToolItem.getType().name()
                                        + " treasury=" + treasury.balance(nation.id()).toPlainString()
                                        + " diplomacy=" + diplomacy.relationBetween(nation.id(), secondNation.id()).name().toLowerCase()
                                        + " policy=" + policy.activePolicy(nation.id()).orElse("none")
                                        + " resources=food:" + resource.amount(nation.id(), "food") + ",ore:" + resource.amount(nation.id(), "ore")
                                        + " resolution=" + enactedResolution.action().kind().name().toLowerCase() + ":" + enactedResolution.state().name().toLowerCase()
                                        + " technology=" + String.join(",", technology.unlockedTechnologies(nation.id()))
                                        + " war=" + war.atWar(nation.id(), secondNation.id())
                                        + " officer=marshal"
                                        + " officerMigration=marshal:member+gui+target+forced"
                                        + " event=" + eventRecord.type()
                                        + " eventFamilies=" + eventFamilySmoke
                                        + " migration=" + migrationSummary
                                        + " resourceExplanationRuntime=" + resourceRuntimeSmoke.summary()
                                        + " protector=" + protectorSmoke
                                        + " claimExplanation=" + claimExplanationSmoke
                                        + " webClaim=command:" + requestResult.pendingId()
                                        + " webClaimFeedback=" + webConfirmFeedback.compact("confirm")
                                        + " onlineWebClaim=" + onlineViewerSmoke
                                        + " nationOperationFeedback=" + nationOperationFeedback
                                        + " eventCommandSources=war+officer+treasury:6"
                                        + " eventCommandSourcesExtended=war+officer+treasury+diplomacy+strategy:14"
                                        + " strategyFeedback=" + strategyFeedback
                                        + " governanceFeedback=" + governanceFeedback
                                        + " viewer=ok"
                                        + " beacon=" + firstDistrict.beaconX() + "," + firstDistrict.beaconY() + "," + firstDistrict.beaconZ() + ":" + beaconType
                                        + " mapSummary=" + mapSummary;
                                    Map<String, Integer> sqliteCounts = loadSqliteCounts();
                                    writeResult(
                                        "PASS",
                                        message,
                                        authenticatedMap.url(),
                                        playerName,
                                        nation.name(),
                                        economy.balance(playerId).toPlainString(),
                                        nation.governmentType().displayName(),
                                        "国家领袖",
                                        viewerFounder,
                                        String.valueOf(nations.experienceOf(nation.id())),
                                        finalClaimCount + "/" + nations.maxClaimsOf(nation.id()),
                                        viewerCityStates,
                                        districts.districtsOf(nation.id()).size() + "/" + districts.districtLimitFor(nation),
                                        viewerProgress,
                                        viewerNextLevel,
                                        "在线",
                                        resourceRuntimeSmoke.lowBalanceUrl(),
                                        resourceRuntimeSmoke.offlineUrl(),
                                        sqliteCounts
                                    );
                                    getLogger().info("STARCORE_SMOKE_PASS " + message);
                                } catch (Throwable finalThrowable) {
                                    writeResult("FAIL", finalThrowable.getMessage() == null ? finalThrowable.getClass().getName() : finalThrowable.getMessage());
                                    getLogger().log(Level.SEVERE, "STARCORE_SMOKE_FAIL " + finalThrowable.getMessage(), finalThrowable);
                                }
                            }, 12L)
                        );
                    } catch (Throwable inner) {
                        writeResult("FAIL", inner.getMessage() == null ? inner.getClass().getName() : inner.getMessage());
                        getLogger().log(Level.SEVERE, "STARCORE_SMOKE_FAIL " + inner.getMessage(), inner);
                    }
                })
            );
            return;
        } catch (Throwable throwable) {
            writeResult("FAIL", throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage());
            getLogger().log(Level.SEVERE, "STARCORE_SMOKE_FAIL " + throwable.getMessage(), throwable);
        }
    }

    private String runProtectorApiSmoke(NationService nations, ExternalProtectionService externalProtection, MapService map, UUID playerId, int baseChunk) throws Exception {
        Plugin protectorPlugin = Bukkit.getPluginManager().getPlugin("ProtectorAPI");
        require(protectorPlugin != null && protectorPlugin.isEnabled(), "ProtectorAPI smoke requested but plugin is not enabled");
        int protectedChunkX = baseChunk + 8;
        int protectedChunkZ = baseChunk + 8;
        String providerName = "MockProtectorSmoke";
        registerSyntheticProtectorModule(protectorPlugin, providerName, "world", protectedChunkX, protectedChunkZ);
        if (externalProtection != null) {
            String summary = externalProtection.summary();
            require(summary.contains("ProtectorAPI 已连接"), "external protection summary did not report connected bridge: " + summary);
            require(summary.contains(providerName), "external protection summary did not include synthetic module: " + summary);
        }

        ChunkClaimSelection protectedSelection = new ChunkClaimSelection("world", protectedChunkX, protectedChunkX + 1, protectedChunkZ, protectedChunkZ + 1);
        ClaimSelectionPreview blockedPreview = nations.previewClaimSelection(playerId, protectedSelection);
        require(!blockedPreview.canSubmit(), "external protection preview unexpectedly allowed submission");
        require(blockedPreview.message().contains("外部保护区域冲突"), "protected preview did not mention external protection conflict: " + blockedPreview.message());
            require(blockedPreview.message().contains(providerName), "protected preview did not include provider name: " + blockedPreview.message());
            assertStatusCommandSummary(providerName);

        expectIllegalState(
            () -> nations.claimSelection(playerId, protectedSelection),
            "claimSelection protected selection",
            providerName
        );
        expectIllegalState(
            () -> nations.claimCurrentChunk(playerId, "world", protectedChunkX, protectedChunkZ),
            "claimCurrentChunk protected chunk",
            providerName
        );

        return "runtime:" + providerName + "@" + protectedChunkX + ":" + protectedChunkZ;
    }

    private String runNationOperationCommandFeedbackSmoke(
        InternalEconomyService economy,
        NationService nations,
        ResolutionService resolutions,
        EventService event
    ) throws Exception {
        PluginCommand starcore = Bukkit.getPluginCommand("starcore");
        require(starcore != null, "starcore command is not registered for nation operation feedback smoke");
        UUID operationPlayerId = UUID.randomUUID();
        String operationPlayerName = "SmokeFounder";
        String operationNationName = "Cmd" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
        String operationCityName = operationNationName + "City";
        economy.deposit(operationPlayerId, new BigDecimal("10000000.00"));
        List<String> messages = new ArrayList<>();
        FeedbackCounters feedback = new FeedbackCounters();
        Player player = commandPlayerProxy(operationPlayerId, operationPlayerName, "world", messages, feedback);

        require(starcore.execute(player, "starcore", new String[] { "nation", "create", operationNationName, "smoke-nation-create" }),
            "nation create command returned false");
        require(messages.stream().anyMatch(message -> message.contains("已创建国家")),
            "nation create command did not report success: " + String.join("\n", messages));
        Nation operationNation = nations.nationByName(operationNationName)
            .orElseThrow(() -> new IllegalStateException("command-created nation not found: " + operationNationName));
        requireEventContext(event, operationNation, "nation.created",
            "actor=" + operationPlayerName,
            "operation=nation-create",
            "target=" + operationNationName,
            "targetId=" + operationNation.id(),
            "members=1",
            "claims=0",
            "reason=smoke-nation-create"
        );

        require(starcore.execute(player, "starcore", new String[] { "nation", "city", "create", operationCityName, "smoke-city-create" }),
            "nation city create command returned false");
        require(messages.stream().anyMatch(message -> message.contains("已创建城邦")),
            "nation city create command did not report success: " + String.join("\n", messages));
        Nation operationCityState = nations.nationByName(operationCityName)
            .orElseThrow(() -> new IllegalStateException("command-created city-state not found: " + operationCityName));
        requireEventContext(event, operationCityState, "city_state.created",
            "actor=" + operationPlayerName,
            "operation=city-state-create",
            "target=" + operationCityName,
            "targetId=" + operationCityState.id(),
            "members=1",
            "claims=0",
            "reason=smoke-city-create"
        );

        ChunkClaimSelection smokeClaimSelection = new ChunkClaimSelection("world", 0, 0, 0, 0);
        ClaimSelectionPreview smokeClaimPreview = nations.previewClaimSelection(operationPlayerId, smokeClaimSelection);
        require(smokeClaimPreview.canSubmit(), "claim preview rejected for smoke founder chunk: " + smokeClaimPreview.message());

        require(starcore.execute(player, "starcore", new String[] { "nation", "claim", "smoke-claim" }),
            "nation claim command returned false");
        require(nations.claimCount(operationNation.id()) == 1,
            "nation claim command did not increase claim count: " + nations.claimCount(operationNation.id()));
        requireEventContext(event, operationNation, "territory.claimed",
            "actor=" + operationPlayerName,
            "operation=territory-claim",
            "target=world:0:0",
            "claims=1",
            "reason=smoke-claim"
        );

        require(starcore.execute(player, "starcore", new String[] { "nation", "unclaim", "smoke-unclaim" }),
            "nation unclaim command returned false");
        require(nations.claimCount(operationNation.id()) == 0,
            "nation unclaim command did not restore claim count to zero: " + nations.claimCount(operationNation.id()));
        requireEventContext(event, operationNation, "territory.unclaimed",
            "actor=" + operationPlayerName,
            "operation=territory-unclaim",
            "target=world:0:0",
            "claims=0",
            "reason=smoke-unclaim"
        );

        UUID joinPlayerId = UUID.randomUUID();
        String joinPlayerName = "SmokeMarshal";
        economy.deposit(joinPlayerId, new BigDecimal("10000000.00"));
        Player joinPlayer = commandPlayerProxy(joinPlayerId, joinPlayerName, "world", messages, feedback, false);
        require(starcore.execute(joinPlayer, "starcore", new String[] { "nation", "join", operationNationName, "smoke-join-request" }),
            "nation join command returned false");
        require(messages.stream().anyMatch(message -> message.contains("已提交入国提案")),
            "nation join command did not report proposal success: " + String.join("\n", messages));
        Resolution joinResolution = requireOpenResolution(resolutions, operationNation, ResolutionKind.JOIN_NATION);
        requireEventContext(event, operationNation, "resolution.proposed",
            "actor=" + joinPlayerName,
            "operation=resolution-propose",
            "target=" + operationNationName,
            "targetId=" + operationNation.id(),
            "resolutionId=" + joinResolution.id(),
            "kind=join_nation",
            "state=open",
            "summary=Join request for " + joinPlayerName,
            "applicant=" + joinPlayerName,
            "applicantId=" + joinPlayerId,
            "reason=smoke-join-request"
        );
        require(starcore.execute(player, "starcore", new String[] { "resolution", "sign", joinResolution.id().toString(), "smoke-join-sign" }),
            "join resolution sign command returned false");
        require(messages.stream().anyMatch(message -> message.contains("签署已记录")),
            "join resolution sign command did not report success: " + String.join("\n", messages));
        Resolution enactedJoinResolution = resolutions.find(joinResolution.id())
            .orElseThrow(() -> new IllegalStateException("join resolution disappeared after signature"));
        require(enactedJoinResolution.state() == ResolutionState.ENACTED,
            "join resolution was not enacted: " + enactedJoinResolution.state());
        require(nations.nationOf(joinPlayerId).filter(foundNation -> foundNation.id().equals(operationNation.id())).isPresent(),
            "join resolution did not add applicant to nation membership");
        requireEventContext(event, operationNation, "resolution.signed",
            "actor=" + operationPlayerName,
            "operation=resolution-sign",
            "target=" + operationNationName,
            "targetId=" + operationNation.id(),
            "resolutionId=" + joinResolution.id(),
            "kind=join_nation",
            "state=enacted",
            "summary=Join request for " + joinPlayerName,
            "applicant=" + joinPlayerName,
            "applicantId=" + joinPlayerId,
            "reason=smoke-join-sign"
        );

        String renameTarget = operationNationName + "Renamed";
        require(starcore.execute(player, "starcore", new String[] { "nation", "rename", renameTarget, "smoke-nation-rename" }),
            "nation rename command returned false");
        require(messages.stream().anyMatch(message -> message.contains("已提交改名提案")),
            "nation rename command did not report proposal success: " + String.join("\n", messages));
        Resolution renameResolution = requireOpenResolution(resolutions, operationNation, ResolutionKind.RENAME_NATION);
        requireEventContext(event, operationNation, "resolution.proposed",
            "actor=" + operationPlayerName,
            "operation=resolution-propose",
            "target=" + operationNationName,
            "targetId=" + operationNation.id(),
            "resolutionId=" + renameResolution.id(),
            "kind=rename_nation",
            "state=open",
            "summary=Rename nation " + operationNationName + " -> " + renameTarget,
            "from=" + operationNationName,
            "to=" + renameTarget,
            "reason=smoke-nation-rename"
        );
        require(starcore.execute(player, "starcore", new String[] { "resolution", "sign", renameResolution.id().toString(), "smoke-resolution-sign" }),
            "resolution sign command returned false");
        require(messages.stream().anyMatch(message -> message.contains("签署已记录")),
            "resolution sign command did not report success: " + String.join("\n", messages));
        Resolution enactedRenameResolution = resolutions.find(renameResolution.id())
            .orElseThrow(() -> new IllegalStateException("rename resolution disappeared after signature"));
        require(enactedRenameResolution.state() == ResolutionState.ENACTED,
            "rename resolution was not enacted: " + enactedRenameResolution.state());
        requireEventContext(event, operationNation, "resolution.signed",
            "actor=" + operationPlayerName,
            "operation=resolution-sign",
            "target=" + renameTarget,
            "targetId=" + operationNation.id(),
            "resolutionId=" + renameResolution.id(),
            "kind=rename_nation",
            "state=enacted",
            "summary=Rename nation " + operationNationName + " -> " + renameTarget,
            "from=" + operationNationName,
            "to=" + renameTarget,
            "reason=smoke-resolution-sign"
        );

        require(feedback.soundCalls.get() >= 8, "nation operation feedback sound was not observed for every operation: " + feedback);
        require(feedback.actionBarCalls.get() >= 8, "nation operation feedback actionbar was not observed for every operation: " + feedback);
        require(feedback.bossBarCalls.get() >= 8, "nation operation feedback bossbar was not observed for every operation: " + feedback);
        require(feedback.titleCalls.get() >= 1, "nation operation feedback title was not observed for nation creation: " + feedback);
        return feedback.compact("operation");
    }

    private String runStrategyCommandFeedbackSmoke(
        InternalEconomyService economy,
        NationService nations,
        TreasuryService treasury,
        PolicyService policy,
        TechnologyService technology,
        WarService war,
        OfficerService officer,
        EventService event
    ) throws Exception {
        PluginCommand starcore = Bukkit.getPluginCommand("starcore");
        require(starcore != null, "starcore command is not registered for strategy feedback smoke");
        UUID strategyPlayerId = UUID.randomUUID();
        String strategyPlayerName = "SmokeStrategist";
        String strategyNationName = "Str" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
        UUID rivalPlayerId = UUID.randomUUID();
        String rivalPlayerName = "SmokeRival";
        String rivalNationName = strategyNationName + "R";
        UUID peaceOfficerId = UUID.randomUUID();
        String peaceOfficerName = "SmokePeaceEnvoy";
        UUID marshalOfficerId = UUID.randomUUID();
        String marshalOfficerName = "SmokeWarMarshal";
        UUID stewardOfficerId = UUID.randomUUID();
        String stewardOfficerName = "SmokeSteward";
        economy.deposit(strategyPlayerId, new BigDecimal("10000000.00"));
        economy.deposit(rivalPlayerId, new BigDecimal("10000000.00"));
        economy.deposit(stewardOfficerId, new BigDecimal("10000000.00"));
        Nation strategyNation = nations.createNation(strategyPlayerId, strategyPlayerName, strategyNationName);
        Nation rivalNation = nations.createNation(rivalPlayerId, rivalPlayerName, rivalNationName);
        require(nations.addMember(strategyNation.id(), stewardOfficerId, stewardOfficerName),
            "steward member join should succeed for strategy smoke nation");
        treasury.deposit(strategyNation.id(), new BigDecimal("1000000.00"));

        List<String> messages = new ArrayList<>();
        FeedbackCounters feedback = new FeedbackCounters();
        Player player = commandPlayerProxy(strategyPlayerId, strategyPlayerName, "world", messages, feedback);

        require(starcore.execute(player, "starcore", new String[] { "policy", "set", "civil_industry", "smoke-policy-founder-set" }),
            "policy set command returned false");
        require(policy.activePolicy(strategyNation.id()).filter("civil_industry"::equals).isPresent(),
            "policy set command did not activate civil_industry: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "policy.set",
            "actor=" + strategyPlayerName,
            "operation=policy-set",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "policy=civil_industry",
            "reason=smoke-policy-founder-set"
        );

        require(starcore.execute(player, "starcore", new String[] { "technology", "unlock", strategyNation.name(), "logistics", "smoke-technology-founder-unlock" }),
            "technology unlock command returned false");
        require(technology.hasTechnology(strategyNation.id(), "logistics"),
            "technology unlock command did not persist logistics: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "technology.unlocked",
            "actor=" + strategyPlayerName,
            "operation=technology-unlock",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "technology=logistics",
            "reason=smoke-technology-founder-unlock"
        );

        require(officer.appoint(strategyNation.id(), "steward", stewardOfficerId, stewardOfficerName),
            "policy/technology steward officer appointment should succeed for strategy smoke nation");
        Player stewardOfficerPlayer = commandPlayerProxy(stewardOfficerId, stewardOfficerName, "world", messages, feedback, false);
        require(starcore.execute(stewardOfficerPlayer, "starcore", new String[] { "policy", "clear", "smoke-policy-steward-clear" }),
            "steward policy clear command returned false");
        require(policy.activePolicy(strategyNation.id()).isEmpty(),
            "steward policy clear command did not clear active policy: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "policy.cleared",
            "actor=" + stewardOfficerName,
            "operation=policy-clear",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "policy=civil_industry",
            "reason=smoke-policy-steward-clear"
        );
        require(starcore.execute(stewardOfficerPlayer, "starcore", new String[] { "policy", "set", "open_diplomacy", "smoke-policy-steward-set" }),
            "steward policy set command returned false");
        require(policy.activePolicy(strategyNation.id()).filter("open_diplomacy"::equals).isPresent(),
            "steward policy set command did not activate open_diplomacy: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "policy.set",
            "actor=" + stewardOfficerName,
            "operation=policy-set",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "policy=open_diplomacy",
            "reason=smoke-policy-steward-set"
        );
        require(starcore.execute(stewardOfficerPlayer, "starcore", new String[] { "technology", "revoke", strategyNation.name(), "logistics", "smoke-technology-steward-revoke" }),
            "steward technology revoke command returned false");
        require(!technology.hasTechnology(strategyNation.id(), "logistics"),
            "steward technology revoke command did not remove logistics: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "technology.revoked",
            "actor=" + stewardOfficerName,
            "operation=technology-revoke",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "technology=logistics",
            "reason=smoke-technology-steward-revoke"
        );
        require(starcore.execute(stewardOfficerPlayer, "starcore", new String[] { "technology", "unlock", strategyNation.name(), "logistics", "smoke-technology-steward-unlock" }),
            "steward technology unlock command returned false");
        require(technology.hasTechnology(strategyNation.id(), "logistics"),
            "steward technology unlock command did not restore logistics: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "technology.unlocked",
            "actor=" + stewardOfficerName,
            "operation=technology-unlock",
            "target=" + strategyNation.name(),
            "targetId=" + strategyNation.id(),
            "technology=logistics",
            "reason=smoke-technology-steward-unlock"
        );

        String warId = smokeWarId(strategyNation, rivalNation);
        require(starcore.execute(player, "starcore", new String[] { "war", "declare", strategyNation.name(), rivalNation.name(), "smoke-war-founder-declare" }),
            "war declare command returned false");
        require(war.atWar(strategyNation.id(), rivalNation.id()),
            "war declare command did not persist war state: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "war.declared",
            "actor=" + strategyPlayerName,
            "operation=war-declare",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "warId=" + warId,
            "reason=smoke-war-founder-declare"
        );

        require(officer.appoint(strategyNation.id(), "diplomat", peaceOfficerId, peaceOfficerName),
            "war-end diplomat officer appointment should succeed for strategy smoke nation");
        Player peaceOfficerPlayer = commandPlayerProxy(peaceOfficerId, peaceOfficerName, "world", messages, feedback, false);
        require(starcore.execute(peaceOfficerPlayer, "starcore", new String[] { "war", "end", strategyNation.name(), rivalNation.name(), "smoke-war-diplomat-end" }),
            "diplomat war end command returned false");
        require(!war.atWar(strategyNation.id(), rivalNation.id()),
            "diplomat war end command did not clear war state: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "war.ended",
            "actor=" + peaceOfficerName,
            "operation=war-end",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "warId=" + warId,
            "reason=smoke-war-diplomat-end"
        );

        require(officer.appoint(strategyNation.id(), "marshal", marshalOfficerId, marshalOfficerName),
            "war-declare marshal officer appointment should succeed for strategy smoke nation");
        Player marshalOfficerPlayer = commandPlayerProxy(marshalOfficerId, marshalOfficerName, "world", messages, feedback, false);
        require(starcore.execute(marshalOfficerPlayer, "starcore", new String[] { "war", "declare", strategyNation.name(), rivalNation.name(), "smoke-war-marshal-declare" }),
            "marshal war declare command returned false");
        require(war.atWar(strategyNation.id(), rivalNation.id()),
            "marshal war declare command did not persist war state: " + String.join("\n", messages));
        requireEventContext(event, strategyNation, "war.declared",
            "actor=" + marshalOfficerName,
            "operation=war-declare",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "warId=" + warId,
            "reason=smoke-war-marshal-declare"
        );

        require(feedback.soundCalls.get() >= 9, "strategy feedback sound was not observed for policy/technology/war: " + feedback);
        require(feedback.actionBarCalls.get() >= 9, "strategy feedback actionbar was not observed for policy/technology/war: " + feedback);
        require(feedback.titleCalls.get() >= 6, "strategy feedback title was not observed for policy/technology/war highlights: " + feedback);
        require(feedback.bossBarCalls.get() >= 9, "strategy feedback bossbar was not observed for policy/technology/war: " + feedback);
        return feedback.compact("strategy")
            + "+policyOfficer=steward:clear+set"
            + "+technologyOfficer=steward:revoke+unlock"
            + "+strategyCommandEvents=policySet+policyClear+technologyUnlock+technologyRevoke:6"
            + "+warOfficer=marshal:declare+diplomat:end"
            + "+warCommandEvents=founderDeclare+diplomatEnd+marshalDeclare:3";
    }

    private String runGovernanceCommandFeedbackSmoke(
        InternalEconomyService economy,
        NationService nations,
        TreasuryService treasury,
        DiplomacyService diplomacy,
        OfficerService officer,
        EventService event,
        ResolutionService resolutions
    ) throws Exception {
        PluginCommand starcore = Bukkit.getPluginCommand("starcore");
        require(starcore != null, "starcore command is not registered for governance feedback smoke");
        UUID governancePlayerId = UUID.randomUUID();
        String governancePlayerName = "SmokeGovernor";
        String governanceNationName = "Gov" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
        UUID rivalPlayerId = UUID.randomUUID();
        String rivalPlayerName = "SmokeNeighbor";
        String rivalNationName = governanceNationName + "R";
        UUID treasuryOfficerId = UUID.randomUUID();
        String treasuryOfficerName = "SmokeTreasurer";
        UUID diplomatOfficerId = UUID.randomUUID();
        String diplomatOfficerName = "SmokeDiplomat";
        economy.deposit(governancePlayerId, new BigDecimal("10000000.00"));
        economy.deposit(rivalPlayerId, new BigDecimal("10000000.00"));
        Nation governanceNation = nations.createNation(governancePlayerId, governancePlayerName, governanceNationName);
        Nation rivalNation = nations.createNation(rivalPlayerId, rivalPlayerName, rivalNationName);
        treasury.deposit(governanceNation.id(), new BigDecimal("10000.00"));

        List<String> messages = new ArrayList<>();
        FeedbackCounters feedback = new FeedbackCounters();
        Player player = commandPlayerProxy(governancePlayerId, governancePlayerName, "world", messages, feedback);

        String diplomacyPairId = smokeWarId(governanceNation, rivalNation);
        require(starcore.execute(player, "starcore", new String[] { "diplomacy", "set", governanceNation.name(), rivalNation.name(), "friendly", "smoke-diplomacy-founder-set" }),
            "diplomacy set command returned false");
        require(diplomacy.relationBetween(governanceNation.id(), rivalNation.id()) == DiplomacyRelation.FRIENDLY,
            "diplomacy set command did not persist friendly relation: " + String.join("\n", messages));
        requireEventContext(event, governanceNation, "diplomacy.updated",
            "actor=" + governancePlayerName,
            "operation=diplomacy-set",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "relation=FRIENDLY",
            "pairId=" + diplomacyPairId,
            "reason=smoke-diplomacy-founder-set"
        );

        require(officer.appoint(governanceNation.id(), "diplomat", diplomatOfficerId, diplomatOfficerName),
            "diplomat officer appointment should succeed for governance smoke nation");
        Player diplomatOfficerPlayer = commandPlayerProxy(diplomatOfficerId, diplomatOfficerName, "world", messages, feedback, false);
        require(starcore.execute(diplomatOfficerPlayer, "starcore", new String[] { "diplomacy", "set", governanceNation.name(), rivalNation.name(), "allied", "smoke-diplomacy-diplomat-set" }),
            "diplomat diplomacy set command returned false");
        require(diplomacy.relationBetween(governanceNation.id(), rivalNation.id()) == DiplomacyRelation.ALLIED,
            "diplomat diplomacy set command did not persist allied relation: " + String.join("\n", messages));
        requireEventContext(event, governanceNation, "diplomacy.updated",
            "actor=" + diplomatOfficerName,
            "operation=diplomacy-set",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "relation=ALLIED",
            "pairId=" + diplomacyPairId,
            "reason=smoke-diplomacy-diplomat-set"
        );

        require(starcore.execute(player, "starcore", new String[] { "government", "propose", "democracy", "smoke-government-propose" }),
            "government propose command returned false");
        Resolution governmentResolution = requireOpenResolution(resolutions, governanceNation, ResolutionKind.CHANGE_GOVERNMENT);
        requireEventContext(event, governanceNation, "resolution.proposed",
            "actor=" + governancePlayerName,
            "operation=resolution-propose",
            "target=" + governanceNation.name(),
            "targetId=" + governanceNation.id(),
            "resolutionId=" + governmentResolution.id(),
            "kind=change_government",
            "state=open",
            "summary=Change government " + governanceNation.governmentType().name() + " -> DEMOCRACY",
            "from=" + governanceNation.governmentType().name(),
            "to=DEMOCRACY",
            "government=DEMOCRACY",
            "reason=smoke-government-propose"
        );

        require(starcore.execute(player, "starcore", new String[] { "diplomacy", "propose", rivalNation.name(), "allied", "smoke-diplomacy-propose" }),
            "diplomacy propose command returned false");
        Resolution diplomacyProposal = requireOpenResolution(resolutions, governanceNation, ResolutionKind.CHANGE_DIPLOMACY_RELATION);
        requireEventContext(event, governanceNation, "resolution.proposed",
            "actor=" + governancePlayerName,
            "operation=resolution-propose",
            "target=" + rivalNation.name(),
            "targetId=" + rivalNation.id(),
            "resolutionId=" + diplomacyProposal.id(),
            "kind=change_diplomacy_relation",
            "state=open",
            "summary=Change diplomacy with " + rivalNation.name() + " -> ALLIED",
            "relation=ALLIED",
            "pairId=" + diplomacyPairId,
            "reason=smoke-diplomacy-propose"
        );

        require(starcore.execute(player, "starcore", new String[] { "officer", "appoint", "marshal", governancePlayerName, "smoke-officer-appoint" }),
            "officer appoint command returned false");
        require(officer.officer(governanceNation.id(), "marshal").filter(appointment ->
            appointment.playerId().equals(governancePlayerId) && appointment.playerName().equals(governancePlayerName)
        ).isPresent(), "officer appoint command did not persist marshal appointment: " + String.join("\n", messages));
        requireEventContext(event, governanceNation, "officer.appointed",
            "actor=" + governancePlayerName,
            "operation=officer-appoint",
            "role=marshal",
            "member=" + governancePlayerName,
            "target=" + governancePlayerName,
            "targetId=" + governancePlayerId,
            "reason=smoke-officer-appoint"
        );

        require(starcore.execute(player, "starcore", new String[] { "officer", "remove", "marshal", "smoke-officer-remove" }),
            "officer remove command returned false");
        require(officer.officer(governanceNation.id(), "marshal").isEmpty(),
            "officer remove command did not clear marshal appointment: " + String.join("\n", messages));
        requireEventContext(event, governanceNation, "officer.removed",
            "actor=" + governancePlayerName,
            "operation=officer-remove",
            "role=marshal",
            "reason=smoke-officer-remove"
        );

        require(starcore.execute(player, "starcore", new String[] { "treasury", "deposit", governanceNation.name(), "5000.00", "smoke-deposit" }),
            "treasury deposit command returned false");
        require(starcore.execute(player, "starcore", new String[] { "treasury", "withdraw", governanceNation.name(), "1200.00", "smoke-withdraw" }),
            "treasury withdraw command returned false");
        require(starcore.execute(player, "starcore", new String[] { "treasury", "reward", governanceNation.name(), "800.00", "smoke-reward" }),
            "treasury reward command returned false");

        require(officer.appoint(governanceNation.id(), "treasurer", treasuryOfficerId, treasuryOfficerName),
            "treasury officer appointment should succeed for governance smoke nation");
        Player treasuryOfficerPlayer = commandPlayerProxy(treasuryOfficerId, treasuryOfficerName, "world", messages, feedback, false);
        require(starcore.execute(treasuryOfficerPlayer, "starcore", new String[] { "treasury", "withdraw", governanceNation.name(), "700.00", "smoke-treasurer-withdraw" }),
            "treasurer withdraw command returned false");

        BigDecimal expectedTreasuryBalance = new BigDecimal("13900.00");
        require(treasury.balance(governanceNation.id()).compareTo(expectedTreasuryBalance) == 0,
            "governance treasury commands produced unexpected balance: expected " + expectedTreasuryBalance + ", actual " + treasury.balance(governanceNation.id()));
        requireEventContext(event, governanceNation, "treasury.withdraw",
            "actor=" + treasuryOfficerName,
            "amount=700.00",
            "balance=13900.00",
            "reason=smoke-treasurer-withdraw"
        );

        require(feedback.soundCalls.get() >= 8, "governance feedback sound was not observed for diplomacy/officer/treasury commands: " + feedback);
        require(feedback.actionBarCalls.get() >= 8, "governance feedback actionbar was not observed for diplomacy/officer/treasury commands: " + feedback);
        require(feedback.titleCalls.get() >= 3, "governance feedback title was not observed for relation/officer/reward highlights: " + feedback);
        require(feedback.bossBarCalls.get() >= 8, "governance feedback bossbar was not observed for diplomacy/officer/treasury commands: " + feedback);
        return feedback.compact("governance")
            + "+diplomacy:2+officer:2+treasury:4+treasuryOfficer=treasurer:withdraw+diplomacyOfficer=diplomat:set"
            + "+diplomacyCommandEvents=founderSet+diplomatSet:2"
            + "+governanceCommandEvents=officerAppoint+officerRemove+treasuryWithdraw:3";
    }

    private void requireEventContext(EventService event, Nation nation, String type, String... expectedContextParts) {
        require(event != null, "EventService is missing for command event context smoke");
        require(nation != null, "nation is missing for command event context smoke");
        String observed = event.eventsOf(nation.id()).stream()
            .filter(record -> type.equals(record.type()))
            .map(record -> record.context() == null ? "" : record.context())
            .collect(java.util.stream.Collectors.joining(" | "));
        boolean matched = event.eventsOf(nation.id()).stream()
            .anyMatch(record -> type.equals(record.type()) && contextContainsAll(record.context(), expectedContextParts));
        require(matched, "missing command event context for " + type + " in " + nation.name()
            + "; expected " + String.join(",", expectedContextParts)
            + "; observed " + observed);
    }

    private boolean contextContainsAll(String context, String... expectedContextParts) {
        String normalized = context == null ? "" : context;
        for (String expected : expectedContextParts) {
            if (expected != null && !expected.isBlank() && !normalized.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private Resolution requireOpenResolution(ResolutionService resolutions, Nation nation, ResolutionKind kind) {
        require(resolutions != null, "ResolutionService is missing for smoke resolution lookup");
        require(nation != null, "nation is missing for smoke resolution lookup");
        return resolutions.openResolutions(nation).stream()
            .filter(resolution -> resolution.action().kind() == kind)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("open resolution not found for " + kind + " in " + nation.name()));
    }

    private String smokeWarId(Nation left, Nation right) {
        String leftId = left.id().toString();
        String rightId = right.id().toString();
        return leftId.compareTo(rightId) <= 0 ? leftId + ":" + rightId : rightId + ":" + leftId;
    }

    private void assertStatusCommandSummary(String providerName) throws Exception {
        PluginCommand starcore = Bukkit.getPluginCommand("starcore");
        require(starcore != null, "starcore command is not registered");
        List<String> messages = new ArrayList<>();
        CommandSender sender = commandSenderProxy(messages);
        boolean handled = starcore.execute(sender, "starcore", new String[] { "status" });
        require(handled, "starcore status command returned false");
        String combined = String.join("\n", messages);
        require(combined.contains("外部保护"), "status output did not contain the external protection label: " + combined);
        require(combined.contains("ProtectorAPI 已连接"), "status output did not report ProtectorAPI connected: " + combined);
        require(combined.contains(providerName), "status output did not include the synthetic protection provider: " + combined);
    }

    private CompletableFuture<WebClaimRequestResult> beginSuccessfulWebClaimSmoke(MapService map, UUID viewerId, ChunkClaimSelection selection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return submitSuccessfulWebClaim(map, viewerId, selection);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private WebClaimRequestResult submitSuccessfulWebClaim(MapService map, UUID viewerId, ChunkClaimSelection selection) throws Exception {
        String personalMap = map.viewerWebAddress(viewerId, true)
            .orElseThrow(() -> new IllegalStateException("personal viewer map URL was not available for success web-claim smoke"));
        HttpTextResponse requestResponse = postRequest(claimApiUri(personalMap, "/api/map/claim/request", selection));
        require(requestResponse.statusCode() == 200, "web claim success request returned unexpected status " + requestResponse.statusCode() + ": " + requestResponse.body());
        require(requestResponse.body().contains("\"requestSubmitted\":true"), "web claim success request did not create a pending request: " + requestResponse.body());
        String pendingId = extractJsonString(requestResponse.body(), "pendingId");
        require(pendingId != null && !pendingId.isBlank(), "web claim success request did not return a pending id: " + requestResponse.body());
        return new WebClaimRequestResult(personalMap, pendingId, requestResponse.body());
    }

    private CompletableFuture<String> beginOnlineViewerWebClaimSmoke(
        MapService map,
        UUID viewerId,
        String viewerName,
        String nationName,
        ChunkClaimSelection selection
    ) throws Exception {
        OnlineViewerProbe viewerProbe = installSyntheticOnlineDirectory(viewerId, viewerName, selection.world());
        return CompletableFuture.supplyAsync(() -> {
            try {
                return submitOnlineViewerWebClaimSmoke(map, viewerId, viewerName, nationName, selection, viewerProbe);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private String submitOnlineViewerWebClaimSmoke(
        MapService map,
        UUID viewerId,
        String viewerName,
        String nationName,
        ChunkClaimSelection selection,
        OnlineViewerProbe viewerProbe
    ) throws Exception {
        List<String> viewerMessages = viewerProbe.messages();
        WebClaimRequestResult requestResult = submitSuccessfulWebClaim(map, viewerId, selection);

        require(viewerMessages.stream().anyMatch(message -> message.contains("网页圈地待确认")),
            "online viewer did not receive pending confirmation message: " + String.join("\n", viewerMessages));
        require(viewerMessages.stream().anyMatch(message -> message.contains("/starcore map confirm " + requestResult.pendingId())),
            "online viewer did not receive confirm command hint: " + String.join("\n", viewerMessages));
        require(viewerMessages.stream().anyMatch(message -> message.contains("/starcore 地图 确认 " + requestResult.pendingId())),
            "online viewer did not receive Chinese confirm command hint: " + String.join("\n", viewerMessages));
        require(viewerMessages.stream().anyMatch(message -> message.contains("/starcore map cancel " + requestResult.pendingId())),
            "online viewer did not receive cancel command hint: " + String.join("\n", viewerMessages));
        require(viewerMessages.stream().anyMatch(message -> message.contains("/starcore 地图 取消 " + requestResult.pendingId())),
            "online viewer did not receive Chinese cancel command hint: " + String.join("\n", viewerMessages));
        require(viewerProbe.feedback().soundCalls.get() >= 1, "online web claim pending feedback sound was not observed: " + viewerProbe.feedback());
        require(viewerProbe.feedback().actionBarCalls.get() >= 1, "online web claim pending feedback actionbar was not observed: " + viewerProbe.feedback());
        require(viewerProbe.feedback().bossBarCalls.get() >= 1, "online web claim pending feedback bossbar was not observed: " + viewerProbe.feedback());

        AuthenticatedMap authenticatedMap = authenticatedMap(map, viewerId);
        String viewerSnapshot = authenticatedMap.snapshot();
        require(viewerSnapshot.contains("\"playerName\":\"" + viewerName + "\""), "online viewer snapshot did not include playerName: " + viewerSnapshot);
        require(viewerSnapshot.contains("\"nationName\":\"" + nationName + "\""), "online viewer snapshot did not include nationName: " + viewerSnapshot);
        require(viewerSnapshot.contains("\"role\":\"founder\""), "online viewer snapshot did not include founder role: " + viewerSnapshot);
        require(viewerSnapshot.contains("\"online\":true"), "online viewer snapshot did not report online=true: " + viewerSnapshot);

        String mapSummary = map.summary();
        require(mapSummary.contains("1 player marker(s)"), "online viewer summary did not expose one player marker: " + mapSummary);
        CompletableFuture<String> cancelSmoke = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                PluginCommand starcore = Bukkit.getPluginCommand("starcore");
                require(starcore != null, "starcore command is not registered");
                List<String> cancelMessages = new ArrayList<>();
                FeedbackCounters cancelFeedback = new FeedbackCounters();
                Player cancelPlayer = commandPlayerProxy(viewerId, viewerName, selection.world(), cancelMessages, cancelFeedback);
                boolean handled = starcore.execute(cancelPlayer, "starcore", new String[] { "map", "cancel", requestResult.pendingId() });
                require(handled, "starcore map cancel command returned false");
                String cancelOutput = String.join("\n", cancelMessages);
                require(cancelOutput.contains("网页圈地请求已取消"), "web claim cancel output did not report cancellation: " + cancelOutput);
                require(cancelOutput.contains("pending-cancelled") || cancelOutput.contains("网页圈地请求已取消"),
                    "web claim cancel output did not include a typed cancellation reason: " + cancelOutput);
                require(cancelFeedback.soundCalls.get() >= 1, "web claim cancel feedback sound was not observed: " + cancelFeedback);
                require(cancelFeedback.actionBarCalls.get() >= 1, "web claim cancel feedback actionbar was not observed: " + cancelFeedback);
                require(cancelFeedback.bossBarCalls.get() >= 1, "web claim cancel feedback bossbar was not observed: " + cancelFeedback);
                cancelSmoke.complete(cancelFeedback.compact("cancel") + "+cancelTyped:1");
            } catch (Throwable throwable) {
                cancelSmoke.completeExceptionally(throwable);
            }
        });
        return viewerName + ":" + requestResult.pendingId() + "+" + viewerProbe.feedback().compact("pending") + "+" + cancelSmoke.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void assertProtectedWebClaimEndpoints(MapService map, UUID viewerId, ChunkClaimSelection protectedSelection, String providerName) throws Exception {
        String personalMap = map.viewerWebAddress(viewerId, true)
            .orElseThrow(() -> new IllegalStateException("personal viewer map URL was not available for ProtectorAPI smoke"));
        HttpTextResponse previewResponse = postRequest(claimApiUri(personalMap, "/api/map/claim/preview", protectedSelection));
        require(previewResponse.statusCode() == 200, "web claim preview returned unexpected status " + previewResponse.statusCode() + ": " + previewResponse.body());
        require(previewResponse.body().contains("\"canSubmit\":false"), "web claim preview did not reject the protected selection: " + previewResponse.body());
        require(previewResponse.body().contains("外部保护区域冲突"), "web claim preview did not mention the external protection conflict: " + previewResponse.body());
        require(previewResponse.body().contains("\"explanation\":{\"state\":\"external-protection-conflict\""), "web claim preview did not include external-protection explanation state: " + previewResponse.body());
        require(previewResponse.body().contains("\"code\":\"external-protection-conflict\""), "web claim preview did not include external-protection explanation reason: " + previewResponse.body());
        require(previewResponse.body().contains(providerName), "web claim preview did not include the synthetic provider: " + previewResponse.body());

        HttpTextResponse requestResponse = postRequest(claimApiUri(personalMap, "/api/map/claim/request", protectedSelection));
        require(requestResponse.statusCode() == 200, "web claim request returned unexpected status " + requestResponse.statusCode() + ": " + requestResponse.body());
        require(requestResponse.body().contains("\"canSubmit\":false"), "web claim request did not reject the protected selection: " + requestResponse.body());
        require(requestResponse.body().contains("\"requestSubmitted\":false"), "web claim request unexpectedly created a pending request: " + requestResponse.body());
        require(requestResponse.body().contains("外部保护区域冲突"), "web claim request did not mention the external protection conflict: " + requestResponse.body());
        require(requestResponse.body().contains("\"explanation\":{\"state\":\"external-protection-conflict\""), "web claim request did not include external-protection explanation state: " + requestResponse.body());
        require(requestResponse.body().contains("\"code\":\"external-protection-conflict\""), "web claim request did not include external-protection explanation reason: " + requestResponse.body());
        require(requestResponse.body().contains(providerName), "web claim request did not include the synthetic provider: " + requestResponse.body());
    }

    private URI claimApiUri(String personalMap, String path, ChunkClaimSelection selection) throws Exception {
        URI viewerUri = URI.create(personalMap);
        String query = viewerUri.getRawQuery()
            + "&world=" + selection.world()
            + "&minX=" + selection.minBlockX()
            + "&maxX=" + selection.maxBlockX()
            + "&minZ=" + selection.minBlockZ()
            + "&maxZ=" + selection.maxBlockZ();
        return new URI(viewerUri.getScheme(), viewerUri.getAuthority(), path, query, null);
    }

    private HttpTextResponse postRequest(URI uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setDoOutput(true);
        connection.getOutputStream().write(new byte[0]);
        int status = connection.getResponseCode();
        try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            String body = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new HttpTextResponse(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return json.substring(valueStart, valueEnd);
    }

    private String extractJsonScalar(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char current = json.charAt(valueEnd);
            if (current == ',' || current == '}' || current == ']') {
                break;
            }
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }

    private long extractJsonLong(String json, String key, long fallback) {
        String raw = extractJsonScalar(json, key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean extractJsonBoolean(String json, String key, boolean fallback) {
        String raw = extractJsonScalar(json, key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw);
    }

    private RuntimeException unwrapAsyncFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException || current instanceof java.util.concurrent.ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        if (current instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(current);
    }

    private void registerSyntheticProtectorModule(Plugin protectorPlugin, String providerName, String worldName, int protectedChunkX, int protectedChunkZ) throws Exception {
        World world = Bukkit.getWorld(worldName);
        require(world != null, "ProtectorAPI smoke world is not loaded: " + worldName);

        ClassLoader loader = protectorPlugin.getClass().getClassLoader();
        Class<?> apiClass = Class.forName("io.github.lijinhong11.protectorapi.ProtectorAPI", true, loader);
        Class<?> moduleInterface = Class.forName("io.github.lijinhong11.protectorapi.protection.IProtectionModule", true, loader);
        Class<?> rangeInterface = Class.forName("io.github.lijinhong11.protectorapi.protection.IProtectionRange", true, loader);
        Class<?> worldCollectionClass = Class.forName("io.github.lijinhong11.protectorapi.objects.WorldCollection", true, loader);
        Class<?> flagStatesClass = Class.forName("io.github.lijinhong11.protectorapi.flag.FlagStates", true, loader);
        Object unsupportedFlagState = flagStatesClass.getField("UNSUPPORTED").get(null);
        Object worldCollection = worldCollectionClass.getConstructor(World.class).newInstance(world);
        String rangeId = "smoke:" + worldName + ":" + protectedChunkX + ":" + protectedChunkZ;
        String displayName = "Smoke Protected Chunk";

        Object rangeProxy = Proxy.newProxyInstance(
            loader,
            new Class<?>[] { rangeInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                return switch (methodName) {
                    case "getId" -> rangeId;
                    case "getDisplayName" -> displayName;
                    case "getWorld" -> worldCollection;
                    case "getFlags" -> Map.of();
                    case "getAdmins", "getMembers" -> java.util.List.of();
                    case "getOwner" -> null;
                    case "getFlagState" -> unsupportedFlagState;
                    case "toString" -> providerName + ":" + rangeId;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
            }
        );

        Object moduleProxy = Proxy.newProxyInstance(
            loader,
            new Class<?>[] { moduleInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                if ("getPluginName".equals(methodName)) {
                    return providerName;
                }
                if ("getProtectionRangeInfos".equals(methodName)) {
                    return java.util.List.of(rangeProxy);
                }
                if ("isInProtectionRange".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Location location) {
                    return location.getWorld() != null
                        && worldName.equals(location.getWorld().getName())
                        && location.getChunk().getX() == protectedChunkX
                        && location.getChunk().getZ() == protectedChunkZ;
                }
                if ("getProtectionRangeInfo".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Location location) {
                    boolean inProtectedChunk = location.getWorld() != null
                        && worldName.equals(location.getWorld().getName())
                        && location.getChunk().getX() == protectedChunkX
                        && location.getChunk().getZ() == protectedChunkZ;
                    return inProtectedChunk ? rangeProxy : null;
                }
                if ("isSupportGlobalFlags".equals(methodName)) {
                    return false;
                }
                if ("getGlobalFlag".equals(methodName)) {
                    return unsupportedFlagState;
                }
                if ("setGlobalFlag".equals(methodName)) {
                    return null;
                }
                return switch (methodName) {
                    case "toString" -> providerName;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                };
            }
        );

        Method registerModule = apiClass.getMethod("register", moduleInterface);
        registerModule.invoke(null, moduleProxy);
    }

    private CommandSender commandSenderProxy(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(),
            new Class<?>[] { CommandSender.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission", "isPermissionSet", "isOp" -> true;
                case "sendMessage" -> {
                    appendMessages(messages, args);
                    yield null;
                }
                case "getName" -> "SmokeAdmin";
                case "name" -> "SmokeAdmin";
                case "spigot" -> null;
                case "toString" -> "SmokeAdmin";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Player commandPlayerProxy(UUID playerId, String playerName, String worldName, List<String> messages) {
        return commandPlayerProxy(playerId, playerName, worldName, messages, null);
    }

    private Player commandPlayerProxy(UUID playerId, String playerName, String worldName, List<String> messages, FeedbackCounters feedbackCounters) {
        return commandPlayerProxy(playerId, playerName, worldName, messages, feedbackCounters, true);
    }

    private Player commandPlayerProxy(UUID playerId, String playerName, String worldName, List<String> messages, FeedbackCounters feedbackCounters, boolean admin) {
        World world = Bukkit.getWorld(worldName);
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission" -> {
                    String permission = args == null || args.length == 0 ? "" : String.valueOf(args[0]);
                    yield switch (permission) {
                        case "starcore.command" -> true;
                        case "starcore.admin" -> admin;
                        default -> admin;
                    };
                }
                case "isPermissionSet" -> true;
                case "isOp" -> admin;
                case "isOnline" -> true;
                case "getUniqueId" -> playerId;
                case "getName", "name", "toString" -> playerName;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0.0D, world == null ? 64.0D : world.getMinHeight() + 1.0D, 0.0D);
                case "sendMessage" -> {
                    appendMessages(messages, args);
                    yield null;
                }
                case "playSound" -> {
                    if (feedbackCounters != null) {
                        feedbackCounters.soundCalls.incrementAndGet();
                    }
                    yield null;
                }
                case "sendActionBar" -> {
                    if (feedbackCounters != null) {
                        feedbackCounters.actionBarCalls.incrementAndGet();
                    }
                    yield null;
                }
                case "showTitle" -> {
                    if (feedbackCounters != null) {
                        feedbackCounters.titleCalls.incrementAndGet();
                    }
                    yield null;
                }
                case "showBossBar" -> {
                    if (feedbackCounters != null) {
                        feedbackCounters.bossBarCalls.incrementAndGet();
                    }
                    yield null;
                }
                case "hideBossBar" -> {
                    if (feedbackCounters != null) {
                        feedbackCounters.bossBarHideCalls.incrementAndGet();
                    }
                    yield null;
                }
                case "spigot" -> null;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private OnlineViewerProbe installSyntheticOnlineDirectory(UUID playerId, String playerName, String worldName) throws Exception {
        Map<UUID, OnlineViewerProbe> probes = installSyntheticOnlineDirectory(List.of(new OnlineViewerSpec(playerId, playerName, worldName)));
        OnlineViewerProbe probe = probes.get(playerId);
        require(probe != null, "synthetic online directory did not create viewer probe for " + playerName);
        return probe;
    }

    private Map<UUID, OnlineViewerProbe> installSyntheticOnlineDirectory(List<OnlineViewerSpec> specs) throws Exception {
        require(specs != null && !specs.isEmpty(), "synthetic online directory needs at least one viewer");
        Map<UUID, Player> players = new java.util.LinkedHashMap<>();
        Map<UUID, OnlineViewerProbe> probes = new java.util.LinkedHashMap<>();
        for (OnlineViewerSpec spec : specs) {
            List<String> messages = new ArrayList<>();
            FeedbackCounters feedbackCounters = new FeedbackCounters();
            Player onlinePlayer = commandPlayerProxy(spec.playerId(), spec.playerName(), spec.worldName(), messages, feedbackCounters);
            players.put(spec.playerId(), onlinePlayer);
            probes.put(spec.playerId(), new OnlineViewerProbe(messages, feedbackCounters));
        }
        OnlinePlayerDirectory directory = new OnlinePlayerDirectory() {
            @Override
            public Optional<Player> findOnlinePlayer(UUID candidateId) {
                return Optional.ofNullable(players.get(candidateId));
            }

            @Override
            public Collection<Player> onlinePlayers() {
                return List.copyOf(players.values());
            }
        };
        installSyntheticOnlineDirectory(directory);
        return probes;
    }

    private void installSyntheticOnlineDirectory(OnlinePlayerDirectory directory) throws Exception {
        Plugin starcorePlugin = Bukkit.getPluginManager().getPlugin("STARCORE");
        require(starcorePlugin != null, "STARCORE plugin is not enabled");
        Method contextMethod = starcorePlugin.getClass().getMethod("context");
        Object context = contextMethod.invoke(starcorePlugin);
        Method serviceRegistryMethod = context.getClass().getMethod("serviceRegistry");
        Object serviceRegistry = serviceRegistryMethod.invoke(context);
        Method registerMethod = serviceRegistry.getClass().getMethod("register", Class.class, Object.class);
        registerMethod.invoke(serviceRegistry, OnlinePlayerDirectory.class, directory);

        Object mapModule = serviceRegistry.getClass().getMethod("require", Class.class)
            .invoke(serviceRegistry, MapService.class);
        java.lang.reflect.Field onlineDirectoryField = mapModule.getClass().getDeclaredField("onlinePlayerDirectory");
        onlineDirectoryField.setAccessible(true);
        onlineDirectoryField.set(mapModule, directory);

        if (mapModule instanceof MapService mapService) {
            mapService.snapshot();
        }
    }

    private ResourceExplanationRuntimeSmoke installResourceExplanationRuntimeSmoke(
        InternalEconomyService economy,
        MapService map,
        NationResourceDistrictService districtService,
        Nation readyNation,
        Nation offlineNation,
        UUID readyViewerId,
        String readyViewerName,
        UUID lowBalanceViewerId,
        String lowBalanceViewerName,
        UUID offlineViewerId,
        String offlineViewerName,
        String worldName,
        int startChunk
    ) throws Exception {
        economy.setBalance(lowBalanceViewerId, new BigDecimal("12.34"));
        installSyntheticOnlineDirectory(List.of(
            new OnlineViewerSpec(readyViewerId, readyViewerName, worldName),
            new OnlineViewerSpec(lowBalanceViewerId, lowBalanceViewerName, worldName)
        ));

        Object nativeService = districtService;
        NationResourceDistrictSnapshot readyDistrict = injectRuntimeResourceDistrict(
            nativeService,
            readyNation,
            worldName,
            startChunk,
            0,
            "smoke_runtime_ready",
            "NONE",
            null
        );
        NationResourceDistrictSnapshot awaitingDistrict = injectRuntimeResourceDistrict(
            nativeService,
            readyNation,
            worldName,
            startChunk,
            1,
            "smoke_runtime_awaiting",
            "AWAITING_TARGET",
            null
        );
        NationResourceDistrictSnapshot waitingDistrict = injectRuntimeResourceDistrict(
            nativeService,
            readyNation,
            worldName,
            startChunk,
            2,
            "smoke_runtime_waiting",
            "WAITING_DEPLETION",
            new ChunkCoordinate(worldName, startChunk + 41, startChunk + 43)
        );
        NationResourceDistrictSnapshot offlineDistrict = injectRuntimeResourceDistrict(
            nativeService,
            offlineNation,
            worldName,
            startChunk,
            3,
            "smoke_runtime_offline",
            "NONE",
            null
        );
        map.snapshot();

        AuthenticatedMap readyMap = authenticatedMap(map, readyViewerId);
        AuthenticatedMap lowBalanceMap = authenticatedMap(map, lowBalanceViewerId);
        AuthenticatedMap offlineMap = authenticatedMap(map, offlineViewerId);
        requireSnapshotContainsRuntimeState(readyMap.snapshot(), "ready", readyDistrict.id(), "ready viewer");
        requireSnapshotContainsRuntimeState(readyMap.snapshot(), "awaiting-target", awaitingDistrict.id(), "ready viewer");
        requireSnapshotContainsRuntimeState(readyMap.snapshot(), "waiting-depletion", waitingDistrict.id(), "ready viewer");
        requireSnapshotContainsRuntimeState(lowBalanceMap.snapshot(), "insufficient-balance", readyDistrict.id(), "low-balance viewer");
        requireSnapshotContainsRuntimeState(offlineMap.snapshot(), "player-offline", offlineDistrict.id(), "offline viewer");
        return new ResourceExplanationRuntimeSmoke(
            "ready+awaiting-target+waiting-depletion+insufficient-balance+player-offline:5",
            lowBalanceMap.url(),
            offlineMap.url()
        );
    }

    private void requireSnapshotContainsRuntimeState(String snapshot, String actionState, UUID districtId, String label) {
        String districtToken = "\"districtId\":\"" + districtId + "\"";
        String actionToken = "\"migrationActionState\":\"" + actionState + "\"";
        String explanationToken = "\"migrationExplanationState\":\"" + actionState + "\"";
        require(snapshot.contains(districtToken), label + " snapshot did not include runtime district " + districtId + ": " + snapshot);
        require(snapshot.contains(actionToken), label + " snapshot did not include action state " + actionState + ": " + snapshot);
        require(snapshot.contains(explanationToken), label + " snapshot did not include explanation state " + actionState + ": " + snapshot);
    }

    private NationResourceDistrictSnapshot injectRuntimeResourceDistrict(
        Object nativeService,
        Nation nation,
        String worldName,
        int startChunk,
        int ordinal,
        String biomeName,
        String migrationStateName,
        ChunkCoordinate pendingTarget
    ) throws Exception {
        ChunkCoordinate coordinate = nextRuntimeResourceCoordinate(nativeService, worldName, startChunk, ordinal);
        Class<?> districtClass = Class.forName("dev.starcore.starcore.module.nation.resource.NationResourceDistrict");
        Constructor<?> constructor = districtClass.getDeclaredConstructor(UUID.class, dev.starcore.starcore.module.nation.model.NationId.class, ChunkCoordinate.class, String.class, double.class);
        constructor.setAccessible(true);
        Object district = constructor.newInstance(UUID.randomUUID(), nation.id(), coordinate, biomeName, 1.0D);

        Method setTotalExperience = districtClass.getDeclaredMethod("setTotalExperience", long.class);
        setTotalExperience.setAccessible(true);
        setTotalExperience.invoke(district, 320L + ordinal * 80L);
        Method setRefreshTimes = districtClass.getDeclaredMethod("setRefreshTimes", long.class, long.class);
        setRefreshTimes.setAccessible(true);
        long now = System.currentTimeMillis();
        setRefreshTimes.invoke(district, now - 60_000L, now + 3_600_000L + ordinal * 60_000L);
        setRuntimeMigrationState(districtClass, district, migrationStateName, pendingTarget, now);

        Field districtsByIdField = nativeService.getClass().getDeclaredField("districtsById");
        districtsByIdField.setAccessible(true);
        Field districtByChunkField = nativeService.getClass().getDeclaredField("districtByChunk");
        districtByChunkField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> districtsById = (Map<UUID, Object>) districtsByIdField.get(nativeService);
        @SuppressWarnings("unchecked")
        Map<String, UUID> districtByChunk = (Map<String, UUID>) districtByChunkField.get(nativeService);
        Method idMethod = districtClass.getDeclaredMethod("id");
        idMethod.setAccessible(true);
        UUID districtId = (UUID) idMethod.invoke(district);
        synchronized (nativeService) {
            String key = runtimeChunkKey(coordinate);
            require(!districtByChunk.containsKey(key), "runtime resource district coordinate was already occupied: " + key);
            districtsById.put(districtId, district);
            districtByChunk.put(key, districtId);
        }

        Method placeBeacon = nativeService.getClass().getDeclaredMethod("placeBeacon", districtClass);
        placeBeacon.setAccessible(true);
        placeBeacon.invoke(nativeService, district);
        NationResourceDistrictSnapshot snapshot = runtimeSnapshot(districtClass, district);
        resetToSingleSyntheticResource(district, snapshot);
        return runtimeSnapshot(districtClass, district);
    }

    private ChunkCoordinate nextRuntimeResourceCoordinate(Object nativeService, String worldName, int startChunk, int ordinal) throws Exception {
        for (int attempt = 0; attempt < 128; attempt++) {
            int x = startChunk + ordinal * 11 + attempt;
            int z = startChunk + ordinal * 13 + attempt;
            ChunkCoordinate coordinate = new ChunkCoordinate(worldName, x, z);
            if (!runtimeResourceCoordinateOccupied(nativeService, coordinate)) {
                return coordinate;
            }
        }
        throw new IllegalStateException("unable to find free runtime resource coordinate near " + worldName + ":" + startChunk);
    }

    private boolean runtimeResourceCoordinateOccupied(Object nativeService, ChunkCoordinate coordinate) throws Exception {
        Field districtByChunkField = nativeService.getClass().getDeclaredField("districtByChunk");
        districtByChunkField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, UUID> districtByChunk = (Map<String, UUID>) districtByChunkField.get(nativeService);
        synchronized (nativeService) {
            return districtByChunk.containsKey(runtimeChunkKey(coordinate));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setRuntimeMigrationState(Class<?> districtClass, Object district, String migrationStateName, ChunkCoordinate pendingTarget, long now) throws Exception {
        Class<?> migrationStateClass = Class.forName("dev.starcore.starcore.module.nation.resource.NationResourceDistrict$MigrationState");
        Object migrationState = Enum.valueOf((Class<Enum>) migrationStateClass.asSubclass(Enum.class), migrationStateName);
        Method setMigration = districtClass.getDeclaredMethod("setMigration", migrationStateClass, ChunkCoordinate.class, long.class, long.class);
        setMigration.setAccessible(true);
        long forceMigrationAt = "WAITING_DEPLETION".equals(migrationStateName) ? now + 14_400_000L : 0L;
        long requestedAt = "NONE".equals(migrationStateName) ? 0L : now;
        setMigration.invoke(district, migrationState, pendingTarget, requestedAt, forceMigrationAt);
    }

    private NationResourceDistrictSnapshot runtimeSnapshot(Class<?> districtClass, Object district) throws Exception {
        Method snapshotMethod = districtClass.getDeclaredMethod("snapshot");
        snapshotMethod.setAccessible(true);
        return (NationResourceDistrictSnapshot) snapshotMethod.invoke(district);
    }

    private String runtimeChunkKey(ChunkCoordinate coordinate) {
        return coordinate.world() + ':' + coordinate.x() + ':' + coordinate.z();
    }

    private void appendMessages(List<String> messages, Object[] args) {
        if (args == null) {
            return;
        }
        for (Object argument : args) {
            appendMessage(messages, argument);
        }
    }

    private void appendMessage(List<String> messages, Object argument) {
        if (argument == null) {
            return;
        }
        if (argument instanceof Component component) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
            return;
        }
        if (argument instanceof String string) {
            messages.add(string);
            return;
        }
        if (argument instanceof Object[] nested) {
            for (Object value : nested) {
                appendMessage(messages, value);
            }
            return;
        }
        messages.add(String.valueOf(argument));
    }

    private void expectIllegalState(CheckedRunnable action, String label, String expectedSnippet) throws Exception {
        try {
            action.run();
            throw new IllegalStateException(label + " unexpectedly succeeded");
        } catch (IllegalStateException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            require(message.contains(expectedSnippet), label + " did not include expected snippet `" + expectedSnippet + "`: " + message);
            require(message.contains("外部保护区域冲突"), label + " did not report external protection conflict: " + message);
        }
    }

    private MigrationSmokeResult runMigrationSmoke(
        InternalEconomyService economy,
        NationResourceDistrictService districtService,
        Nation nation,
        NationResourceDistrictSnapshot districtSnapshot,
        ChunkClaimSelection selection,
        UUID playerId,
        String playerName
    ) throws Exception {
        Object nativeService = districtService;
        NationResourceDistrictSnapshot liveSnapshot = currentDistrictSnapshot(districtService, nation.id(), districtSnapshot.id());
        Object district = internalDistrict(nativeService, liveSnapshot.id());
        Block syntheticResourceBlock = resetToSingleSyntheticResource(district, liveSnapshot);

        AtomicReference<ItemStack> deliveredCore = new AtomicReference<>();
        AtomicBoolean clearedHand = new AtomicBoolean(false);
        AtomicReference<Inventory> openedInventory = new AtomicReference<>();
        AtomicInteger feedbackSoundCalls = new AtomicInteger();
        AtomicInteger feedbackActionBarCalls = new AtomicInteger();
        AtomicInteger feedbackTitleCalls = new AtomicInteger();
        AtomicInteger feedbackBossBarCalls = new AtomicInteger();
        AtomicInteger feedbackBossBarHideCalls = new AtomicInteger();
        Player player = playerProxy(
            playerId,
            playerName,
            deliveredCore,
            clearedHand,
            openedInventory,
            liveSnapshot.coordinate().world(),
            feedbackSoundCalls,
            feedbackActionBarCalls,
            feedbackTitleCalls,
            feedbackBossBarCalls,
            feedbackBossBarHideCalls
        );
        BigDecimal balanceBefore = economy.balance(playerId);

        openMigrationThroughBeaconGui(nativeService, liveSnapshot, player, openedInventory);

        ItemStack migrationCore = deliveredCore.get();
        require(migrationCore != null, "migration core was not delivered");
        require(migrationCore.getType() == Material.NETHER_STAR, "migration core material was " + migrationCore.getType());
        require(economy.balance(playerId).compareTo(balanceBefore) < 0, "migration confirmation did not charge the player");

        NationResourceDistrictSnapshot awaiting = districtService.districtAt(liveSnapshot.coordinate())
            .orElseThrow(() -> new IllegalStateException("district disappeared after begin migration"));
        require("awaiting_target".equals(awaiting.migrationState()), "expected awaiting_target, got " + awaiting.migrationState());

        ChunkCoordinate target = selection.coordinates().stream()
            .filter(coordinate -> !coordinate.equals(liveSnapshot.coordinate()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no alternate claimed target chunk available"));
        Method handleTarget = nativeService.getClass().getDeclaredMethod("handleMigrationTarget", Player.class, Block.class, ItemStack.class, EquipmentSlot.class);
        handleTarget.setAccessible(true);
        handleTarget.invoke(nativeService, player, blockProxy(target), migrationCore, EquipmentSlot.HAND);

        require(clearedHand.get(), "migration core was not consumed from hand");
        NationResourceDistrictSnapshot waiting = districtService.districtAt(liveSnapshot.coordinate())
            .orElseThrow(() -> new IllegalStateException("district disappeared after target selection"));
        require("waiting_depletion".equals(waiting.migrationState()), "expected waiting_depletion, got " + waiting.migrationState());
        require(target.equals(waiting.pendingTarget()), "migration pending target mismatch: " + waiting.pendingTarget() + " != " + target);

        if (nativeService instanceof org.bukkit.event.Listener listener) {
            BlockBreakEvent breakEvent = new BlockBreakEvent(syntheticResourceBlock, player);
            Method onBlockBreak = nativeService.getClass().getDeclaredMethod("onBlockBreak", BlockBreakEvent.class);
            onBlockBreak.setAccessible(true);
            onBlockBreak.invoke(listener, breakEvent);
            require(!breakEvent.isCancelled(), "synthetic resource block break was unexpectedly cancelled");
        } else {
            throw new IllegalStateException("district service is not a Bukkit listener");
        }

        NationResourceDistrictSnapshot movedSnapshot = districtService.districtAt(target)
            .orElseThrow(() -> new IllegalStateException("district was not present at depleted target"));
        require("none".equals(movedSnapshot.migrationState()), "expected none after depletion migration, got " + movedSnapshot.migrationState());

        NationResourceDistrictSnapshot liveMovedSnapshot = currentDistrictSnapshot(districtService, nation.id(), movedSnapshot.id());
        Object movedDistrict = internalDistrict(nativeService, liveMovedSnapshot.id());
        resetToSingleSyntheticResource(movedDistrict, liveMovedSnapshot);
        deliveredCore.set(null);
        clearedHand.set(false);
        openedInventory.set(null);
        BigDecimal forcedBalanceBefore = economy.balance(playerId);

        openMigrationThroughBeaconGui(nativeService, liveMovedSnapshot, player, openedInventory);

        ItemStack forcedMigrationCore = deliveredCore.get();
        require(forcedMigrationCore != null, "second migration core was not delivered");
        require(forcedMigrationCore.getType() == Material.NETHER_STAR, "second migration core material was " + forcedMigrationCore.getType());
        require(economy.balance(playerId).compareTo(forcedBalanceBefore) < 0, "second migration confirmation did not charge the player");

        NationResourceDistrictSnapshot secondAwaiting = districtService.districtAt(liveMovedSnapshot.coordinate())
            .orElseThrow(() -> new IllegalStateException("district disappeared after second begin migration"));
        require("awaiting_target".equals(secondAwaiting.migrationState()), "expected second awaiting_target, got " + secondAwaiting.migrationState());

        ChunkCoordinate forcedTarget = selection.coordinates().stream()
            .filter(coordinate -> !coordinate.equals(liveMovedSnapshot.coordinate()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no alternate forced-migration target chunk available"));
        handleTarget.invoke(nativeService, player, blockProxy(forcedTarget), forcedMigrationCore, EquipmentSlot.HAND);

        require(clearedHand.get(), "second migration core was not consumed from hand");
        NationResourceDistrictSnapshot secondWaiting = districtService.districtAt(target)
            .orElseThrow(() -> new IllegalStateException("district disappeared after second target selection"));
        require("waiting_depletion".equals(secondWaiting.migrationState()), "expected second waiting_depletion, got " + secondWaiting.migrationState());
        require(forcedTarget.equals(secondWaiting.pendingTarget()), "second migration pending target mismatch: " + secondWaiting.pendingTarget() + " != " + forcedTarget);

        setForceMigrationAt(internalDistrict(nativeService, secondWaiting.id()), 1L);
        invokeRefreshAllDistricts(nativeService);

        NationResourceDistrictSnapshot forcedSnapshot = districtService.districtAt(forcedTarget)
            .orElseThrow(() -> new IllegalStateException("district was not present at forced-migration target"));
        require("none".equals(forcedSnapshot.migrationState()), "expected none after forced migration, got " + forcedSnapshot.migrationState());
        require(districtService.districtAt(liveMovedSnapshot.coordinate()).isEmpty(), "district still remained at pre-force chunk after forced migration");
        require(feedbackSoundCalls.get() >= 5, "resource district feedback sound calls were not observed: " + feedbackSoundCalls.get());
        require(feedbackActionBarCalls.get() >= 3, "resource district feedback actionbar calls were not observed: " + feedbackActionBarCalls.get());
        require(feedbackTitleCalls.get() >= 2, "resource district feedback title calls were not observed: " + feedbackTitleCalls.get());
        require(feedbackBossBarCalls.get() >= 4, "resource district feedback bossbar calls were not observed: " + feedbackBossBarCalls.get());
        FeedbackEffectProbe effectProbe = runFeedbackEffectProbe(nativeService, liveSnapshot.coordinate().world());
        require(effectProbe.worldSoundCalls() >= effectProbe.eventCount(),
            "resource district feedback world sound calls were not observed for every event: " + effectProbe);
        require(effectProbe.particleCalls() >= effectProbe.eventCount(),
            "resource district feedback particle calls were not observed for every event: " + effectProbe);
        String baseSummary = "gui+mined:" + target
            + "+forced:" + forcedTarget
            + "+feedbackSound:" + feedbackSoundCalls.get()
            + "+worldSound:" + effectProbe.worldSoundCalls()
            + "+particles:" + effectProbe.particleCalls()
            + "+actionbar:" + feedbackActionBarCalls.get()
            + "+title:" + feedbackTitleCalls.get()
            + "+bossbar:" + feedbackBossBarCalls.get();
        return new MigrationSmokeResult(baseSummary, feedbackBossBarCalls, feedbackBossBarHideCalls);
    }

    private NationResourceDistrictSnapshot currentDistrictSnapshot(
        NationResourceDistrictService districtService,
        dev.starcore.starcore.module.nation.model.NationId nationId,
        UUID districtId
    ) {
        return districtService.districtsOf(nationId).stream()
            .filter(snapshot -> snapshot.id().equals(districtId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("current district snapshot not found: " + districtId));
    }

    private void openMigrationThroughBeaconGui(
        Object nativeService,
        NationResourceDistrictSnapshot districtSnapshot,
        Player player,
        AtomicReference<Inventory> openedInventory
    ) throws Exception {
        World world = Bukkit.getWorld(districtSnapshot.coordinate().world());
        require(world != null, "beacon GUI world is not loaded: " + districtSnapshot.coordinate().world());
        Block beacon = world.getBlockAt(districtSnapshot.beaconX(), districtSnapshot.beaconY(), districtSnapshot.beaconZ());
        String coordinateLine = "区块: " + districtSnapshot.coordinate();

        Method onPlayerInteract = nativeService.getClass().getDeclaredMethod("onPlayerInteract", PlayerInteractEvent.class);
        onPlayerInteract.setAccessible(true);
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, beacon, BlockFace.UP, EquipmentSlot.HAND);
        onPlayerInteract.invoke(nativeService, interactEvent);
        require(interactEvent.isCancelled(), "resource beacon right click was not cancelled");

        Inventory statusMenu = openedInventory.getAndSet(null);
        if (!resourceMenuMatches(statusMenu, Boolean.FALSE, districtSnapshot.id(), coordinateLine)) {
            statusMenu = latestResourceMenu(nativeService, null, Boolean.FALSE, districtSnapshot.id(), coordinateLine);
        }
        require(statusMenu != null, "resource beacon did not open the status menu");
        require(statusMenu.getItem(13) != null && statusMenu.getItem(13).getType() == Material.EMERALD, "status menu migration button was missing");
        assertMenuItem(statusMenu, 10, Material.BEACON, "资源区块状态",
            coordinateLine,
            "理论单轮资源:",
            "理论单轮经验:",
            "理论每小时资源:",
            "理论每小时经验:",
            "理论刷新周期:",
            "未来3轮产出:",
            "未来3轮预计时长:");
        assertMenuItem(statusMenu, 16, Material.CLOCK, "迁移状态",
            "当前状态:",
            "当前阶段:",
            "下一步:",
            "限制:",
            "未来3轮产出:");

        Method onInventoryClick = nativeService.getClass().getDeclaredMethod("onInventoryClick", InventoryClickEvent.class);
        onInventoryClick.setAccessible(true);
        InventoryClickEvent migrateClick = inventoryClick(player, statusMenu, 13);
        onInventoryClick.invoke(nativeService, migrateClick);
        require(migrateClick.isCancelled(), "status menu migration click was not cancelled");

        Inventory confirmMenu = openedInventory.getAndSet(null);
        if (!resourceMenuMatches(confirmMenu, Boolean.TRUE, districtSnapshot.id(), coordinateLine)) {
            confirmMenu = latestResourceMenu(nativeService, statusMenu, Boolean.TRUE, districtSnapshot.id(), coordinateLine);
        }
        require(confirmMenu != null, "resource migration confirmation menu did not open");
        require(confirmMenu != statusMenu, "resource migration confirmation reused the status menu instance");
        require(confirmMenu.getItem(11) != null && confirmMenu.getItem(11).getType() == Material.LIME_CONCRETE, "confirmation menu confirm button was missing");
        assertMenuItem(confirmMenu, 10, Material.BEACON, "当前资源区块状态",
            coordinateLine,
            "当前状态:");
        assertMenuItem(confirmMenu, 13, Material.CLOCK, "迁移后运营预估",
            "理论单轮资源:",
            "理论单轮经验:",
            "理论每小时资源:",
            "理论每小时经验:",
            "理论刷新周期:",
            "未来3轮产出:",
            "未来3轮预计时长:",
            "下次刷新:",
            "强制迁移时间:");
        assertMenuItem(confirmMenu, 11, Material.LIME_CONCRETE, "确认支付并领取迁移核心",
            "费用:",
            "之后右键一个本国领地区块设置目标");

        InventoryClickEvent confirmClick = inventoryClick(player, confirmMenu, 11);
        onInventoryClick.invoke(nativeService, confirmClick);
        require(confirmClick.isCancelled(), "migration confirmation click was not cancelled");

        Inventory refreshedStatusMenu = openedInventory.getAndSet(null);
        if (!resourceMenuMatches(refreshedStatusMenu, Boolean.FALSE, districtSnapshot.id(), coordinateLine)) {
            refreshedStatusMenu = latestResourceMenu(nativeService, statusMenu, Boolean.FALSE, districtSnapshot.id(), coordinateLine);
        }
        require(refreshedStatusMenu != null, "resource status menu did not reopen after migration confirmation");
        assertMenuItem(refreshedStatusMenu, 13, Material.NETHER_STAR, "等待设置迁移目标",
            "结果:",
            "当前迁移状态:",
            "当前阶段:",
            "下一步:",
            "限制:");
    }

    private void assertMenuItem(Inventory inventory, int slot, Material expectedType, String expectedNameSnippet, String... loreSnippets) {
        ItemStack item = inventory.getItem(slot);
        require(item != null, "menu item missing at slot " + slot);
        require(item.getType() == expectedType, "menu item at slot " + slot + " had type " + item.getType() + " instead of " + expectedType);
        String displayName = plainDisplayName(item);
        require(displayName.contains(expectedNameSnippet), "menu item at slot " + slot + " display name `" + displayName + "` did not include `" + expectedNameSnippet + "`");
        List<String> lore = plainLore(item);
        for (String loreSnippet : loreSnippets) {
            require(lore.stream().anyMatch(line -> line.contains(loreSnippet)),
                "menu item at slot " + slot + " lore did not include `" + loreSnippet + "`: " + lore);
        }
    }

    private String plainDisplayName(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        Component displayName = meta.displayName();
        if (displayName != null) {
            return PlainTextComponentSerializer.plainText().serialize(displayName);
        }
        String legacy = meta.getDisplayName();
        return legacy == null ? "" : legacy;
    }

    private List<String> plainLore(ItemStack item) {
        if (item == null) {
            return List.of();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        List<Component> components = meta.lore();
        if (components != null) {
            return components.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        }
        List<String> legacy = meta.getLore();
        return legacy == null ? List.of() : legacy;
    }

    @SuppressWarnings("unchecked")
    private Inventory latestResourceMenu(
        Object nativeService,
        Inventory excludedInventory,
        Boolean expectedConfirmation,
        UUID expectedDistrictId,
        String expectedCoordinateLine
    ) throws Exception {
        java.lang.reflect.Field resourceMenusField = nativeService.getClass().getDeclaredField("resourceMenus");
        resourceMenusField.setAccessible(true);
        Map<Inventory, ?> resourceMenus = (Map<Inventory, ?>) resourceMenusField.get(nativeService);
        Inventory fallback = null;
        for (Map.Entry<Inventory, ?> entry : resourceMenus.entrySet()) {
            Inventory inventory = entry.getKey();
            if (inventory == null || inventory == excludedInventory) {
                continue;
            }
            Object holder = entry.getValue();
            if (holder == null) {
                holder = inventory.getHolder();
            }
            if (holder == null) {
                continue;
            }
            if (expectedConfirmation != null && !expectedConfirmation.equals(resourceMenuConfirmation(holder))) {
                continue;
            }
            if (expectedDistrictId != null && !expectedDistrictId.equals(resourceMenuDistrictId(holder))) {
                continue;
            }
            if (expectedCoordinateLine == null || resourceMenuContainsLore(inventory, 10, expectedCoordinateLine)) {
                return inventory;
            }
            if (fallback == null) {
                fallback = inventory;
            }
        }
        return fallback;
    }

    private boolean resourceMenuMatches(
        Inventory inventory,
        Boolean expectedConfirmation,
        UUID expectedDistrictId,
        String expectedCoordinateLine
    ) throws Exception {
        if (inventory == null) {
            return false;
        }
        Object holder = inventory.getHolder();
        if (holder == null) {
            return false;
        }
        if (expectedConfirmation != null && !expectedConfirmation.equals(resourceMenuConfirmation(holder))) {
            return false;
        }
        if (expectedDistrictId != null && !expectedDistrictId.equals(resourceMenuDistrictId(holder))) {
            return false;
        }
        return expectedCoordinateLine == null || resourceMenuContainsLore(inventory, 10, expectedCoordinateLine);
    }

    private Boolean resourceMenuConfirmation(Object holder) throws Exception {
        java.lang.reflect.Field confirmationField = holder.getClass().getDeclaredField("confirmation");
        confirmationField.setAccessible(true);
        return (Boolean) confirmationField.get(holder);
    }

    private UUID resourceMenuDistrictId(Object holder) throws Exception {
        java.lang.reflect.Field districtIdField = holder.getClass().getDeclaredField("districtId");
        districtIdField.setAccessible(true);
        return (UUID) districtIdField.get(holder);
    }

    private boolean resourceMenuContainsLore(Inventory inventory, int slot, String expectedLoreSnippet) {
        ItemStack item = inventory.getItem(slot);
        if (item == null) {
            return false;
        }
        return plainLore(item).stream().anyMatch(line -> line.contains(expectedLoreSnippet));
    }

    private InventoryClickEvent inventoryClick(Player player, Inventory topInventory, int slot) {
        return new InventoryClickEvent(
            inventoryView(player, topInventory),
            InventoryType.SlotType.CONTAINER,
            slot,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
    }

    private InventoryView inventoryView(Player player, Inventory topInventory) {
        return (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[] { InventoryView.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getTopInventory" -> topInventory;
                case "getBottomInventory" -> player.getInventory();
                case "getPlayer" -> player;
                case "getType" -> InventoryType.CHEST;
                case "setItem" -> {
                    int slot = (Integer) args[0];
                    if (slot >= 0 && slot < topInventory.getSize()) {
                        topInventory.setItem(slot, (ItemStack) args[1]);
                    }
                    yield null;
                }
                case "getItem" -> {
                    int slot = (Integer) args[0];
                    yield slot >= 0 && slot < topInventory.getSize() ? topInventory.getItem(slot) : null;
                }
                case "setCursor", "open", "close", "setTitle" -> null;
                case "getCursor" -> null;
                case "getInventory" -> {
                    int slot = (Integer) args[0];
                    yield slot >= 0 && slot < topInventory.getSize() ? topInventory : player.getInventory();
                }
                case "convertSlot" -> args[0];
                case "getSlotType" -> InventoryType.SlotType.CONTAINER;
                case "countSlots" -> topInventory.getSize();
                case "setProperty" -> false;
                case "getTitle", "getOriginalTitle" -> "STARCORE Smoke Resource District";
                case "getMenuType" -> null;
                case "toString" -> "STARCORE Smoke InventoryView";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private AuthenticatedMap authenticatedMap(MapService map, UUID playerId) throws Exception {
        String personalMap = map.viewerWebAddress(playerId, true)
            .orElseThrow(() -> new IllegalStateException("personal viewer map URL was not available"));
        URI viewerUri = URI.create(personalMap);
        URI snapshotUri = new URI(viewerUri.getScheme(), viewerUri.getAuthority(), "/api/map/snapshot", viewerUri.getQuery(), null);
        try (java.io.InputStream input = snapshotUri.toURL().openStream()) {
            return new AuthenticatedMap(personalMap, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Object internalDistrict(Object nativeService, UUID districtId) throws Exception {
        Method synchronizedDistrict = nativeService.getClass().getDeclaredMethod("synchronizedDistrict", UUID.class);
        synchronizedDistrict.setAccessible(true);
        Object optional = synchronizedDistrict.invoke(nativeService, districtId);
        if (optional instanceof java.util.Optional<?> value && value.isPresent()) {
            return value.get();
        }
        throw new IllegalStateException("internal district not found: " + districtId);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Block resetToSingleSyntheticResource(Object district, NationResourceDistrictSnapshot snapshot) throws Exception {
        Method resourceBlocksMethod = district.getClass().getDeclaredMethod("resourceBlocks");
        resourceBlocksMethod.setAccessible(true);
        java.util.List resourceBlocks = (java.util.List) resourceBlocksMethod.invoke(district);
        resourceBlocks.clear();
        Class<?> locationClass = Class.forName("dev.starcore.starcore.module.nation.resource.ResourceBlockLocation");
        Constructor<?> constructor = locationClass.getDeclaredConstructor(String.class, int.class, int.class, int.class, Material.class);
        constructor.setAccessible(true);
        int y = Math.max(1, snapshot.beaconY() - 1);
        resourceBlocks.add(constructor.newInstance(snapshot.coordinate().world(), snapshot.beaconX(), y, snapshot.beaconZ(), Material.IRON_ORE));
        return blockAtProxy(snapshot.coordinate().world(), snapshot.beaconX(), y, snapshot.beaconZ());
    }

    private Player playerProxy(
        UUID playerId,
        String playerName,
        AtomicReference<ItemStack> deliveredCore,
        AtomicBoolean clearedHand,
        AtomicReference<Inventory> openedInventory,
        String worldName,
        AtomicInteger feedbackSoundCalls,
        AtomicInteger feedbackActionBarCalls,
        AtomicInteger feedbackTitleCalls,
        AtomicInteger feedbackBossBarCalls,
        AtomicInteger feedbackBossBarHideCalls
    ) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[] { PlayerInventory.class },
            (proxy, method, args) -> {
                if ("addItem".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemStack[] items && items.length > 0) {
                        deliveredCore.set(items[0]);
                    }
                    return new HashMap<Integer, ItemStack>();
                }
                if ("setItem".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof EquipmentSlot) {
                    clearedHand.set(true);
                    return null;
                }
                return defaultValue(method.getReturnType());
            }
        );
        World world = Bukkit.getWorld(worldName);
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "isOnline" -> true;
                case "getUniqueId" -> playerId;
                case "getName" -> playerName;
                case "getInventory" -> inventory;
                case "hasPermission" -> false;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0.0D, world == null ? 64.0D : world.getMinHeight() + 1.0D, 0.0D);
                case "playSound" -> {
                    feedbackSoundCalls.incrementAndGet();
                    yield null;
                }
                case "sendActionBar" -> {
                    feedbackActionBarCalls.incrementAndGet();
                    yield null;
                }
                case "showTitle" -> {
                    feedbackTitleCalls.incrementAndGet();
                    yield null;
                }
                case "showBossBar" -> {
                    feedbackBossBarCalls.incrementAndGet();
                    yield null;
                }
                case "hideBossBar" -> {
                    feedbackBossBarHideCalls.incrementAndGet();
                    yield null;
                }
                case "openInventory" -> {
                    if (args != null && args.length > 0) {
                        if (args[0] instanceof Inventory inventoryToOpen) {
                            openedInventory.set(inventoryToOpen);
                        } else if (args[0] instanceof InventoryView inventoryViewToOpen) {
                            openedInventory.set(inventoryViewToOpen.getTopInventory());
                        }
                    }
                    yield null;
                }
                case "sendMessage", "closeInventory" -> null;
                case "toString" -> playerName;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private record MigrationSmokeResult(String baseSummary, AtomicInteger bossBarCalls, AtomicInteger bossBarHideCalls) {
        String summary() {
            int shown = bossBarCalls.get();
            int hidden = bossBarHideCalls.get();
            if (shown < 4) {
                throw new IllegalStateException("resource district feedback bossbar calls were not observed: " + shown);
            }
            if (hidden < shown) {
                throw new IllegalStateException("resource district feedback bossbar hide calls did not catch up: shown=" + shown + ", hidden=" + hidden);
            }
            return baseSummary + "+bossbarHide:" + hidden;
        }
    }

    private FeedbackEffectProbe runFeedbackEffectProbe(Object nativeService, String worldName) throws Exception {
        AtomicInteger worldSoundCalls = new AtomicInteger();
        AtomicInteger particleCalls = new AtomicInteger();
        World world = feedbackWorldProxy(worldName, worldSoundCalls, particleCalls);
        Location location = new Location(world, 0.5D, 65.5D, 0.5D);
        Object feedbackSupport = newFeedbackSupport(nativeService);
        Method emit = feedbackSupport.getClass().getDeclaredMethod("emit", String.class, Player.class, Location.class);
        emit.setAccessible(true);
        List<String> eventKeys = List.of(
            "migration-started",
            "migration-target-selected",
            "migration-completed",
            "migration-completed-forced",
            "migration-blocked",
            "resource-refreshed",
            "resource-mined"
        );
        for (String eventKey : eventKeys) {
            emit.invoke(feedbackSupport, eventKey, null, location);
        }
        return new FeedbackEffectProbe(worldSoundCalls.get(), particleCalls.get(), eventKeys.size());
    }

    private Object newFeedbackSupport(Object nativeService) throws Exception {
        Field feedbackField = nativeService.getClass().getDeclaredField("feedbackSupport");
        feedbackField.setAccessible(true);
        return feedbackField.get(nativeService);
    }

    private World feedbackWorldProxy(String worldName, AtomicInteger worldSoundCalls, AtomicInteger particleCalls) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] { World.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> worldName;
                case "getMinHeight" -> 0;
                case "getMaxHeight" -> 320;
                case "getSeaLevel" -> 63;
                case "isChunkLoaded" -> true;
                case "playSound" -> {
                    worldSoundCalls.incrementAndGet();
                    yield null;
                }
                case "spawnParticle" -> {
                    particleCalls.incrementAndGet();
                    yield null;
                }
                case "toString" -> "SmokeFeedbackWorld:" + worldName;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private record FeedbackEffectProbe(int worldSoundCalls, int particleCalls, int eventCount) {
    }

    private Block blockProxy(ChunkCoordinate coordinate) {
        World world = Bukkit.getWorld(coordinate.world());
        Chunk chunk = (Chunk) Proxy.newProxyInstance(
            Chunk.class.getClassLoader(),
            new Class<?>[] { Chunk.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getX" -> coordinate.x();
                case "getZ" -> coordinate.z();
                case "getWorld" -> world;
                case "toString" -> coordinate.toString();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
        return (Block) Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] { Block.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getChunk" -> chunk;
                case "toString" -> coordinate.toString();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Block blockAtProxy(String worldName, int x, int y, int z) {
        World world = Bukkit.getWorld(worldName);
        Chunk chunk = (Chunk) Proxy.newProxyInstance(
            Chunk.class.getClassLoader(),
            new Class<?>[] { Chunk.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getX" -> Math.floorDiv(x, 16);
                case "getZ" -> Math.floorDiv(z, 16);
                case "getWorld" -> world;
                case "toString" -> worldName + ":" + Math.floorDiv(x, 16) + ":" + Math.floorDiv(z, 16);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
        return (Block) Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] { Block.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getChunk" -> chunk;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "toString" -> worldName + ":" + x + ":" + y + ":" + z;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private void setForceMigrationAt(Object district, long forceMigrationAtMillis) throws Exception {
        java.lang.reflect.Field forceField = district.getClass().getDeclaredField("forceMigrationAtMillis");
        forceField.setAccessible(true);
        forceField.setLong(district, forceMigrationAtMillis);
    }

    private void invokeRefreshAllDistricts(Object nativeService) throws Exception {
        Method method = nativeService.getClass().getDeclaredMethod("refreshAllDistricts");
        method.setAccessible(true);
        method.invoke(nativeService);
    }

    private Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Double.TYPE) return 0.0D;
        if (type == Float.TYPE) return 0.0F;
        if (type == Short.TYPE) return (short) 0;
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Character.TYPE) return '\0';
        return null;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void writeResult(String status, String message) {
        writeResult(
            status,
            message,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            Map.of()
        );
    }

    private void writeResult(
        String status,
        String message,
        String browserUrl,
        String viewerName,
        String viewerNation,
        String viewerBalance,
        String viewerGovernment,
        String viewerRole,
        String viewerFounder,
        String viewerExperience,
        String viewerClaims,
        String viewerCityStates,
        String viewerResources,
        String viewerProgress,
        String viewerNextLevel,
        String viewerOnline,
        String resourceRuntimeLowUrl,
        String resourceRuntimeOfflineUrl,
        Map<String, Integer> sqliteCounts
    ) {
        File output = new File(getServer().getWorldContainer(), "starcore-smoke-harness-result.properties");
        try (FileWriter writer = new FileWriter(output, StandardCharsets.UTF_8)) {
            writer.write("status=" + status + "\n");
            writer.write("message=" + message.replace('\n', ' ') + "\n");
            if (browserUrl != null && !browserUrl.isBlank()) {
                writer.write("browser_url=" + browserUrl.replace('\n', ' ') + "\n");
            }
            if (viewerName != null && !viewerName.isBlank()) {
                writer.write("viewer_name=" + viewerName.replace('\n', ' ') + "\n");
            }
            if (viewerNation != null && !viewerNation.isBlank()) {
                writer.write("viewer_nation=" + viewerNation.replace('\n', ' ') + "\n");
            }
            if (viewerBalance != null && !viewerBalance.isBlank()) {
                writer.write("viewer_balance=" + viewerBalance.replace('\n', ' ') + "\n");
            }
            if (viewerGovernment != null && !viewerGovernment.isBlank()) {
                writer.write("viewer_government=" + viewerGovernment.replace('\n', ' ') + "\n");
            }
            if (viewerRole != null && !viewerRole.isBlank()) {
                writer.write("viewer_role=" + viewerRole.replace('\n', ' ') + "\n");
            }
            if (viewerFounder != null && !viewerFounder.isBlank()) {
                writer.write("viewer_founder=" + viewerFounder.replace('\n', ' ') + "\n");
            }
            if (viewerExperience != null && !viewerExperience.isBlank()) {
                writer.write("viewer_experience=" + viewerExperience.replace('\n', ' ') + "\n");
            }
            if (viewerClaims != null && !viewerClaims.isBlank()) {
                writer.write("viewer_claims=" + viewerClaims.replace('\n', ' ') + "\n");
            }
            if (viewerCityStates != null && !viewerCityStates.isBlank()) {
                writer.write("viewer_city_states=" + viewerCityStates.replace('\n', ' ') + "\n");
            }
            if (viewerResources != null && !viewerResources.isBlank()) {
                writer.write("viewer_resources=" + viewerResources.replace('\n', ' ') + "\n");
            }
            if (viewerProgress != null && !viewerProgress.isBlank()) {
                writer.write("viewer_progress=" + viewerProgress.replace('\n', ' ') + "\n");
            }
            if (viewerNextLevel != null && !viewerNextLevel.isBlank()) {
                writer.write("viewer_next_level=" + viewerNextLevel.replace('\n', ' ') + "\n");
            }
            if (viewerOnline != null && !viewerOnline.isBlank()) {
                writer.write("viewer_online=" + viewerOnline.replace('\n', ' ') + "\n");
            }
            if (resourceRuntimeLowUrl != null && !resourceRuntimeLowUrl.isBlank()) {
                writer.write("resource_runtime_low_url=" + resourceRuntimeLowUrl.replace('\n', ' ') + "\n");
            }
            if (resourceRuntimeOfflineUrl != null && !resourceRuntimeOfflineUrl.isBlank()) {
                writer.write("resource_runtime_offline_url=" + resourceRuntimeOfflineUrl.replace('\n', ' ') + "\n");
            }
            if (sqliteCounts != null && !sqliteCounts.isEmpty()) {
                for (Map.Entry<String, Integer> entry : sqliteCounts.entrySet()) {
                    writer.write("sqlite." + entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Unable to write smoke result file", exception);
        }
    }

    private Map<String, Integer> loadSqliteCounts() throws Exception {
        Plugin starcorePlugin = Bukkit.getPluginManager().getPlugin("STARCORE");
        require(starcorePlugin != null, "STARCORE plugin is not enabled");
        Path databasePath = starcorePlugin.getDataFolder().toPath().resolve("starcore.db").toAbsolutePath().normalize();
        require(java.nio.file.Files.exists(databasePath), "STARCORE sqlite database does not exist: " + databasePath);

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databasePath);

        List<String> tables = List.of(
            "starcore_metadata",
            "starcore_territory_claims",
            "starcore_player_balances",
            "starcore_nation_state",
            "starcore_nation_resource_district_state",
            "starcore_diplomacy_state",
            "starcore_treasury_state",
            "starcore_event_state",
            "starcore_officer_state",
            "starcore_policy_state",
            "starcore_resource_state",
            "starcore_technology_state",
            "starcore_war_state",
            "starcore_resolution_state"
        );

        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String table : tables) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                     ResultSet result = statement.executeQuery()) {
                    counts.put(table, result.next() ? result.getInt(1) : 0);
                }
            }
        }
        return Map.copyOf(counts);
    }

    private record AuthenticatedMap(String url, String snapshot) {
    }

    private record OnlineViewerProbe(List<String> messages, FeedbackCounters feedback) {
    }

    private record OnlineViewerSpec(UUID playerId, String playerName, String worldName) {
    }

    private record ResourceExplanationRuntimeSmoke(String summary, String lowBalanceUrl, String offlineUrl) {
    }

    private static final class FeedbackCounters {
        private final AtomicInteger soundCalls = new AtomicInteger();
        private final AtomicInteger actionBarCalls = new AtomicInteger();
        private final AtomicInteger titleCalls = new AtomicInteger();
        private final AtomicInteger bossBarCalls = new AtomicInteger();
        private final AtomicInteger bossBarHideCalls = new AtomicInteger();

        private String compact(String prefix) {
            return prefix + "Sound:" + soundCalls.get()
                + "+" + prefix + "Actionbar:" + actionBarCalls.get()
                + "+" + prefix + "Title:" + titleCalls.get()
                + "+" + prefix + "Bossbar:" + bossBarCalls.get()
                + "+" + prefix + "BossbarHide:" + bossBarHideCalls.get();
        }

        @Override
        public String toString() {
            return compact("feedback");
        }
    }

    private record WebClaimRequestResult(String personalMap, String pendingId, String body) {
    }

    private record HttpTextResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
'@

$pluginYml = @'
name: STARCORESmokeHarness
version: 1.0.0
main: dev.starcore.smoke.StarCoreSmokeHarnessPlugin
api-version: '1.21'
load: POSTWORLD
depend: [STARCORE]
softdepend: [ProtectorAPI]
'@

$javaFile = Join-Path $sourceDir 'StarCoreSmokeHarnessPlugin.java'
$pluginYmlFile = Join-Path $classesDir 'plugin.yml'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[string]$javaSource = $javaSource.Replace('__PROTECTOR_API_SMOKE__', $(if ($ProtectorApiSmoke) { 'true' } else { 'false' }))
[System.IO.File]::WriteAllText($javaFile, $javaSource, $utf8NoBom)
[System.IO.File]::WriteAllText($pluginYmlFile, $pluginYml, $utf8NoBom)

$classpathFile = Join-Path $harnessRoot 'provided-classpath.txt'
& mvn -q -f (Join-Path $ProjectRoot 'pom.xml') dependency:build-classpath "-Dmdep.outputFile=$classpathFile" "-Dmdep.includeScope=provided"
if ($LASTEXITCODE -ne 0) {
    throw "mvn dependency:build-classpath failed with exit code $LASTEXITCODE"
}
$dependencyClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$compileClasspath = "$dependencyClasspath;$StarCoreJar"

& javac -encoding UTF-8 -cp $compileClasspath -d $classesDir $javaFile
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
& jar --create --file $harnessJar -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath $StarCoreJar -Destination (Join-Path $PluginsDir 'starcore-0.1.0-SNAPSHOT.jar') -Force
if ($ProtectorApiSmoke) {
    $installedProtectorApiJar = Join-Path $PluginsDir ([System.IO.Path]::GetFileName($resolvedProtectorApiJar))
    Copy-Item -LiteralPath $resolvedProtectorApiJar -Destination $installedProtectorApiJar -Force
}
Get-ChildItem -LiteralPath $PluginsDir -Filter 'starcore-smoke-harness*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
$installedHarness = Join-Path $PluginsDir 'starcore-smoke-harness.jar'
Copy-Item -LiteralPath $harnessJar -Destination $installedHarness -Force

$paperGlobalConfig = Join-Path $ServerRoot 'config\paper-global.yml'
$paperGlobalOriginal = $null
if (Test-Path -LiteralPath $paperGlobalConfig) {
    $paperGlobalOriginal = Get-Content -LiteralPath $paperGlobalConfig -Raw -Encoding UTF8
    $paperGlobalAdjusted = $paperGlobalOriginal -replace '(?m)^(\s*early-warning-delay:\s*)\d+\s*$', '${1}120000'
    $paperGlobalAdjusted = $paperGlobalAdjusted -replace '(?m)^(\s*early-warning-every:\s*)\d+\s*$', '${1}120000'
    [System.IO.File]::WriteAllText($paperGlobalConfig, $paperGlobalAdjusted, $utf8NoBom)
}

$proc = $null
$healthStatus = 'not-started'
$markerStatus = 'not-seen'
$markerLine = ''
$browserSmokeResult = $null
$webClaimSmokeResult = $null
try {
    $proc = Start-Process -FilePath 'java' -ArgumentList @('-Xmx1G', '-Xms512M', '-jar', $PaperJar, 'nogui') -WorkingDirectory $ServerRoot -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8716/api/map/health' -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $healthStatus = '200'
                $response.Content | Set-Content -LiteralPath $healthFile -Encoding UTF8
            } else {
                $healthStatus = [string]$response.StatusCode
            }
        } catch {
            $healthStatus = $_.Exception.Message
        }
        if (Test-Path -LiteralPath $outLog) {
            $tail = Get-Content -LiteralPath $outLog -Tail 200 -ErrorAction SilentlyContinue
            $pass = $tail | Select-String -Pattern 'STARCORE_SMOKE_PASS' | Select-Object -Last 1
            $fail = $tail | Select-String -Pattern 'STARCORE_SMOKE_FAIL' | Select-Object -Last 1
            if ($pass) {
                $markerStatus = 'PASS'
                $markerLine = $pass.Line
            } elseif ($fail) {
                $markerStatus = 'FAIL'
                $markerLine = $fail.Line
            }
        }
        if (($markerStatus -eq 'PASS' -and $healthStatus -eq '200') -or $markerStatus -eq 'FAIL' -or $proc.HasExited) {
            break
        }
    }
    $resultProperties = $null
    if (($ProtectorApiSmoke -or $BrowserSmoke) -and $markerStatus -eq 'PASS' -and $healthStatus -eq '200') {
        $resultProperties = Read-SmokeResultProperties (Join-Path $ServerRoot 'starcore-smoke-harness-result.properties')
        Assert-SmokeSqliteCounts -ResultProperties $resultProperties
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'officerMigration=marshal:member\+gui\+target\+forced') {
        throw "Paper smoke marker did not prove marshal resource-district migration authorization: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'resourceExplanationRuntime=ready\+awaiting-target\+waiting-depletion\+insufficient-balance\+player-offline:5') {
        throw "Paper smoke marker did not prove real runtime resource explanation states: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'eventFamilies=finance\+war\+officer\+diplomacy\+strategy\+territory\+nation:7') {
        throw "Paper smoke marker did not prove multi-family event ledger context fixtures: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'eventCommandSources=war\+officer\+treasury:6') {
        throw "Paper smoke marker did not prove real command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'eventCommandSourcesExtended=war\+officer\+treasury\+diplomacy\+strategy:14') {
        throw "Paper smoke marker did not prove extended command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'strategyCommandEvents=policySet\+policyClear\+technologyUnlock\+technologyRevoke:6') {
        throw "Paper smoke marker did not prove strategy command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'diplomacyCommandEvents=founderSet\+diplomatSet:2') {
        throw "Paper smoke marker did not prove diplomacy command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'warCommandEvents=founderDeclare\+diplomatEnd\+marshalDeclare:3') {
        throw "Paper smoke marker did not prove war command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'governanceCommandEvents=officerAppoint\+officerRemove\+treasuryWithdraw:3') {
        throw "Paper smoke marker did not prove governance command-sourced event contexts: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'treasuryOfficer=treasurer:withdraw') {
        throw "Paper smoke marker did not prove treasurer treasury-withdraw authorization: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'diplomacyOfficer=diplomat:set') {
        throw "Paper smoke marker did not prove diplomat diplomacy-set authorization: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'warOfficer=marshal:declare\+diplomat:end') {
        throw "Paper smoke marker did not prove war declare/end officer authorization: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'policyOfficer=steward:clear\+set') {
        throw "Paper smoke marker did not prove steward policy set/clear authorization: $markerLine"
    }
    if ($markerStatus -eq 'PASS' -and $markerLine -notmatch 'technologyOfficer=steward:revoke\+unlock') {
        throw "Paper smoke marker did not prove steward technology unlock/revoke authorization: $markerLine"
    }
    if ($ProtectorApiSmoke -and $markerStatus -eq 'PASS' -and $healthStatus -eq '200') {
        if ($markerLine -notmatch 'protector=(?<protector>[^\s]+)') {
            throw "Could not extract protector summary from smoke marker: $markerLine"
        }
        $webClaimSmokeResult = Invoke-StarCoreProtectedClaimApiSmoke `
            -BrowserUrl $resultProperties['browser_url'] `
            -ProtectorSummary $Matches['protector']
    }
    if ($BrowserSmoke -and $markerStatus -eq 'PASS' -and $healthStatus -eq '200') {
        $browserSmokeResult = Invoke-StarCoreMapBrowserSmoke `
            -BrowserUrl $resultProperties['browser_url'] `
            -ViewerName $resultProperties['viewer_name'] `
            -ViewerNation $resultProperties['viewer_nation'] `
            -ViewerBalance $resultProperties['viewer_balance'] `
            -ViewerGovernment $resultProperties['viewer_government'] `
            -ViewerRole $resultProperties['viewer_role'] `
            -ViewerFounder $resultProperties['viewer_founder'] `
            -ViewerExperience $resultProperties['viewer_experience'] `
            -ViewerClaims $resultProperties['viewer_claims'] `
            -ViewerCityStates $resultProperties['viewer_city_states'] `
            -ViewerResources $resultProperties['viewer_resources'] `
            -ViewerProgress $resultProperties['viewer_progress'] `
            -ViewerNextLevel $resultProperties['viewer_next_level'] `
            -ViewerOnline $resultProperties['viewer_online'] `
            -ResourceRuntimeLowUrl $resultProperties['resource_runtime_low_url'] `
            -ResourceRuntimeOfflineUrl $resultProperties['resource_runtime_offline_url'] `
            -DomFile $browserDomFile `
            -ScreenshotFile $browserScreenshotFile `
            -MobileScreenshotFile $browserMobileScreenshotFile `
            -HarnessRoot $harnessRoot `
            -ExplicitBrowserPath $BrowserPath `
            -BrowserScript (Join-Path $ProjectRoot 'scripts\smoke-starcore-map-browser.mjs')
    }
} finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
        Wait-Process -Id $proc.Id -Timeout 15 -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $starCoreDataDir) {
        Copy-Item -LiteralPath $starCoreDataDir -Destination $starCoreDataArtifactDir -Recurse -Force
    }
    if ($ProtectorApiSmoke -and (Test-Path -LiteralPath $protectorApiDataDir)) {
        Copy-Item -LiteralPath $protectorApiDataDir -Destination $protectorApiDataArtifactDir -Recurse -Force
    }
    if (Test-Path -LiteralPath $installedHarness) {
        Remove-Item -LiteralPath $installedHarness -Force
    }
    if ($ProtectorApiSmoke -and -not [string]::IsNullOrWhiteSpace($installedProtectorApiJar) -and (Test-Path -LiteralPath $installedProtectorApiJar)) {
        Remove-Item -LiteralPath $installedProtectorApiJar -Force
    }
    if ($null -ne $paperGlobalOriginal) {
        [System.IO.File]::WriteAllText($paperGlobalConfig, $paperGlobalOriginal, $utf8NoBom)
    }
    if ($hadStarCoreData) {
        if (Test-Path -LiteralPath $starCoreDataDir) {
            Remove-Item -LiteralPath $starCoreDataDir -Recurse -Force
        }
        Move-Item -LiteralPath $starCoreDataBackupDir -Destination $starCoreDataDir
    } elseif (Test-Path -LiteralPath $starCoreDataDir) {
        Remove-Item -LiteralPath $starCoreDataDir -Recurse -Force
    }
    if ($ProtectorApiSmoke) {
        if ($hadProtectorApiData) {
            if (Test-Path -LiteralPath $protectorApiDataDir) {
                Remove-Item -LiteralPath $protectorApiDataDir -Recurse -Force
            }
            Move-Item -LiteralPath $protectorApiDataBackupDir -Destination $protectorApiDataDir
        } elseif (Test-Path -LiteralPath $protectorApiDataDir) {
            Remove-Item -LiteralPath $protectorApiDataDir -Recurse -Force
        }
        if (Test-Path -LiteralPath $protectorApiJarBackupDir) {
            Get-ChildItem -LiteralPath $protectorApiJarBackupDir -File | ForEach-Object {
                Move-Item -LiteralPath $_.FullName -Destination (Join-Path $PluginsDir $_.Name)
            }
        }
    }
}

$listen = Get-NetTCPConnection -LocalPort 8716 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,State,OwningProcess
$errorMatches = @()
if (Test-Path -LiteralPath $outLog) {
    $errorMatches = Select-String -LiteralPath $outLog -Pattern 'ERROR|Exception|Could not load|Could not enable|MissingResource|MessageFormat' -CaseSensitive:$false | Select-Object -First 30
}
$summaryJson = Join-Path $harnessRoot 'smoke-summary.json'

[pscustomobject]@{
    Health = $healthStatus
    Marker = $markerStatus
    MarkerLine = $markerLine
    OutLog = $outLog
    ErrLog = $errLog
    HealthFile = $healthFile
    HarnessJar = $harnessJar
    ProtectorApiSmoke = if ($ProtectorApiSmoke) { 'enabled' } else { 'disabled' }
    ProtectorApiJar = if ($ProtectorApiSmoke) { $resolvedProtectorApiJar } else { '' }
    WebClaimSmoke = if ($webClaimSmokeResult) { $webClaimSmokeResult.Status } elseif ($ProtectorApiSmoke) { 'not-run' } else { 'disabled' }
    WebClaimSmokeDetails = if ($webClaimSmokeResult) { $webClaimSmokeResult.Details } else { '' }
    BrowserSmoke = if ($browserSmokeResult) { $browserSmokeResult.Status } elseif ($BrowserSmoke) { 'not-run' } else { 'disabled' }
    BrowserSmokeDetails = if ($browserSmokeResult) { $browserSmokeResult.Details } else { '' }
    BrowserDom = if (Test-Path -LiteralPath $browserDomFile) { $browserDomFile } else { '' }
    BrowserScreenshot = if (Test-Path -LiteralPath $browserScreenshotFile) { $browserScreenshotFile } else { '' }
    BrowserMobileScreenshot = if (Test-Path -LiteralPath $browserMobileScreenshotFile) { $browserMobileScreenshotFile } else { '' }
    StarCoreDataArtifact = if (Test-Path -LiteralPath $starCoreDataArtifactDir) { $starCoreDataArtifactDir } else { '' }
    ProtectorApiDataArtifact = if (Test-Path -LiteralPath $protectorApiDataArtifactDir) { $protectorApiDataArtifactDir } else { '' }
    StarCoreDataRestored = if ($hadStarCoreData) { Test-Path -LiteralPath $starCoreDataDir } else { -not (Test-Path -LiteralPath $starCoreDataDir) }
    ProtectorApiDataRestored = if ($ProtectorApiSmoke) { if ($hadProtectorApiData) { Test-Path -LiteralPath $protectorApiDataDir } else { -not (Test-Path -LiteralPath $protectorApiDataDir) } } else { $null }
    JavaProcessExited = if ($proc) { $proc.HasExited } else { $null }
    PortListen = if ($listen) { ($listen | Out-String).Trim() } else { 'none' }
    ErrorMatches = if ($errorMatches) { ($errorMatches | ForEach-Object { $_.Line }) -join "`n" } else { 'none' }
} | Tee-Object -Variable smokeResult | Format-List

$summaryPayload = New-SmokeSummary `
    -ResultProperties $resultProperties `
    -Health $smokeResult.Health `
    -Marker $smokeResult.Marker `
    -MarkerLine $smokeResult.MarkerLine `
    -OutLog $smokeResult.OutLog `
    -ErrLog $smokeResult.ErrLog `
    -HealthFile $smokeResult.HealthFile `
    -HarnessJar $smokeResult.HarnessJar `
    -ProtectorApiSmoke $smokeResult.ProtectorApiSmoke `
    -ProtectorApiJar $smokeResult.ProtectorApiJar `
    -WebClaimSmokeResult $webClaimSmokeResult `
    -BrowserSmokeResult $browserSmokeResult `
    -BrowserDom $smokeResult.BrowserDom `
    -BrowserScreenshot $smokeResult.BrowserScreenshot `
    -BrowserMobileScreenshot $smokeResult.BrowserMobileScreenshot `
    -StarCoreDataArtifact $smokeResult.StarCoreDataArtifact `
    -ProtectorApiDataArtifact $smokeResult.ProtectorApiDataArtifact `
    -StarCoreDataRestored $smokeResult.StarCoreDataRestored `
    -ProtectorApiDataRestored $smokeResult.ProtectorApiDataRestored `
    -JavaProcessExited $smokeResult.JavaProcessExited `
    -PortListen $smokeResult.PortListen `
    -ErrorMatches $smokeResult.ErrorMatches

$summaryPayload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryJson -Encoding UTF8

if ($markerStatus -ne 'PASS' -or $healthStatus -ne '200') {
    exit 1
}
if ($BrowserSmoke -and -not $browserSmokeResult) {
    exit 1
}
