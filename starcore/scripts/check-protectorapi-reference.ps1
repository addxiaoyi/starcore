[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$ReferenceRoot = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

function Test-RequiredRegex {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$Label
    )
    if (-not [regex]::IsMatch($Content, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        throw "ProtectorAPI reference contract missing: $Label"
    }
}

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

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$referenceRootPath = if ([string]::IsNullOrWhiteSpace($ReferenceRoot)) {
    Resolve-RequiredPath -Path (Join-Path $projectRootPath 'references\ProtectorAPI') -Label 'ProtectorAPI reference root'
} else {
    Resolve-RequiredPath -Path $ReferenceRoot -Label 'ProtectorAPI reference root'
}

$apiFile = Resolve-RequiredPath -Path (Join-Path $referenceRootPath 'api\src\main\java\io\github\lijinhong11\protectorapi\ProtectorAPI.java') -Label 'ProtectorAPI reference api class'
$moduleFile = Resolve-RequiredPath -Path (Join-Path $referenceRootPath 'api\src\main\java\io\github\lijinhong11\protectorapi\protection\IProtectionModule.java') -Label 'ProtectorAPI reference module interface'
$rangeFile = Resolve-RequiredPath -Path (Join-Path $referenceRootPath 'api\src\main\java\io\github\lijinhong11\protectorapi\protection\IProtectionRange.java') -Label 'ProtectorAPI reference range interface'
$bridgeContractFile = Resolve-RequiredPath -Path (Join-Path $projectRootPath 'src\main\java\dev\starcore\starcore\foundation\protection\ProtectorApiBridgeContract.java') -Label 'STARCORE ProtectorApiBridgeContract'
$pluginDescriptorFile = Resolve-RequiredPath -Path (Join-Path $projectRootPath 'src\main\resources\plugin.yml') -Label 'STARCORE plugin.yml'
$serviceDescriptorFile = Resolve-RequiredPath -Path (Join-Path $projectRootPath 'src\main\resources\META-INF\services\dev.starcore.starcore.foundation.protection.ExternalProtectionBridgeProvider') -Label 'STARCORE external protection bridge service descriptor'

$apiContent = Get-Content -LiteralPath $apiFile -Raw -Encoding UTF8
$moduleContent = Get-Content -LiteralPath $moduleFile -Raw -Encoding UTF8
$rangeContent = Get-Content -LiteralPath $rangeFile -Raw -Encoding UTF8
$bridgeContractContent = Get-Content -LiteralPath $bridgeContractFile -Raw -Encoding UTF8
$pluginDescriptorContent = Get-Content -LiteralPath $pluginDescriptorFile -Raw -Encoding UTF8
$serviceDescriptorContent = Get-Content -LiteralPath $serviceDescriptorFile -Raw -Encoding UTF8

Test-RequiredRegex -Content $apiContent -Pattern 'public\s+static\s+[^\r\n]*findModule\s*\(\s*Location\s+\w+\s*\)' -Label 'ProtectorAPI.findModule(Location)'
Test-RequiredRegex -Content $apiContent -Pattern 'public\s+static\s+[^\r\n]*getAllAvailableProtectionModules\s*\(\s*\)' -Label 'ProtectorAPI.getAllAvailableProtectionModules()'
Test-RequiredRegex -Content $moduleContent -Pattern 'String\s+getPluginName\s*\(\s*\)' -Label 'IProtectionModule.getPluginName()'
Test-RequiredRegex -Content $moduleContent -Pattern 'getProtectionRangeInfo\s*\(\s*(?:@[A-Za-z0-9_$.]+\s+)*Location\s+\w+\s*\)' -Label 'IProtectionModule.getProtectionRangeInfo(Location)'
Test-RequiredRegex -Content $rangeContent -Pattern 'String\s+getDisplayName\s*\(\s*\)' -Label 'IProtectionRange.getDisplayName()'
Test-RequiredRegex -Content $rangeContent -Pattern 'String\s+getId\s*\(\s*\)' -Label 'IProtectionRange.getId()'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'PLUGIN_NAME\s*=\s*"ProtectorAPI"' -Label 'ProtectorApiBridgeContract.PLUGIN_NAME'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(apiClass,\s*"findModule",\s*Location\.class\)' -Label 'ProtectorApiBridgeContract findModule binding'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(apiClass,\s*"getAllAvailableProtectionModules"\s*\)' -Label 'ProtectorApiBridgeContract module listing binding'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(moduleClass,\s*"getPluginName"\s*\)' -Label 'ProtectorApiBridgeContract plugin name binding'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(moduleClass,\s*"getProtectionRangeInfo",\s*Location\.class\)' -Label 'ProtectorApiBridgeContract range info binding'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(rangeClass,\s*"getDisplayName"\s*\)' -Label 'ProtectorApiBridgeContract display name binding'
Test-RequiredRegex -Content $bridgeContractContent -Pattern 'requiredMethod\(rangeClass,\s*"getId"\s*\)' -Label 'ProtectorApiBridgeContract id binding'
Test-RequiredRegex -Content $pluginDescriptorContent -Pattern 'softdepend:\s*\[\s*ProtectorAPI\s*\]' -Label 'plugin.yml softdepend ProtectorAPI'
Test-RequiredRegex -Content $serviceDescriptorContent -Pattern 'dev\.starcore\.starcore\.foundation\.protection\.ProtectorApiBridgeProvider' -Label 'ServiceLoader provider registration'

$head = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', 'HEAD')
$branch = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'rev-parse', '--abbrev-ref', 'HEAD')
$remote = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'config', '--get', 'remote.origin.url')
$headDecorated = Get-GitValueOrEmpty -Arguments @('-C', $referenceRootPath, 'log', '--oneline', '-1', '--decorate')

$outputDirectory = Ensure-Directory -Path (Join-Path $projectRootPath ('target\protectorapi-reference-checks\check-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
$summaryPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Join-Path $outputDirectory 'protectorapi-reference-check-summary.json'
} else {
    [System.IO.Path]::GetFullPath($OutputPath)
}
$summaryParent = Split-Path -Path $summaryPath -Parent
if (-not [string]::IsNullOrWhiteSpace($summaryParent)) {
    Ensure-Directory -Path $summaryParent | Out-Null
}

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o')
    status = 'ok'
    projectRoot = $projectRootPath
    referenceRoot = $referenceRootPath
    summaryPath = $summaryPath
    reference = [ordered]@{
        remote = $remote
        branch = $branch
        head = $head
        headDecorated = $headDecorated
        apiFile = $apiFile
        moduleFile = $moduleFile
        rangeFile = $rangeFile
    }
    starcore = [ordered]@{
        bridgeContractFile = $bridgeContractFile
        pluginDescriptorFile = $pluginDescriptorFile
        serviceDescriptorFile = $serviceDescriptorFile
    }
    checks = @(
        'ProtectorAPI.findModule(Location)',
        'ProtectorAPI.getAllAvailableProtectionModules()',
        'IProtectionModule.getPluginName()',
        'IProtectionModule.getProtectionRangeInfo(Location)',
        'IProtectionRange.getDisplayName()',
        'IProtectionRange.getId()',
        'ProtectorApiBridgeContract bindings',
        'plugin.yml softdepend ProtectorAPI',
        'ServiceLoader provider registration'
    )
}

$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
$result | ConvertTo-Json -Depth 8
