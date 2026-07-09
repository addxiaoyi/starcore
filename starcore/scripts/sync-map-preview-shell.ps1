[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [string]$PreviewRoot = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'starcore-runtime-common.ps1')

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$projectRootPath = Resolve-RequiredPath -Path $ProjectRoot -Label 'Project root'
$previewRootPath = if ([string]::IsNullOrWhiteSpace($PreviewRoot)) {
    Join-Path (Split-Path $projectRootPath -Parent) 'map'
} else {
    $PreviewRoot
}
$previewRootPath = Resolve-RequiredPath -Path $previewRootPath -Label 'Preview root'

$sourceRoot = Resolve-RequiredPath -Path (Join-Path $projectRootPath 'src\main\resources\web\map') -Label 'Source web map root'

$copyPairs = @(
    @{
        Source = Join-Path $sourceRoot 'index.html'
        Target = Join-Path $previewRootPath 'index.html'
    },
    @{
        Source = Join-Path $sourceRoot 'css\styles.css'
        Target = Join-Path $previewRootPath 'css\styles.css'
    },
    @{
        Source = Join-Path $sourceRoot 'js\map.js'
        Target = Join-Path $previewRootPath 'js\map.js'
    }
)

$results = foreach ($pair in $copyPairs) {
    $sourcePath = Resolve-RequiredPath -Path $pair.Source -Label 'Map preview source file'
    $targetParent = Ensure-Directory -Path (Split-Path -Path $pair.Target -Parent)
    Copy-Item -LiteralPath $sourcePath -Destination $pair.Target -Force

    [ordered]@{
        source = $sourcePath
        target = [System.IO.Path]::GetFullPath($pair.Target)
        hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pair.Target).Hash
    }
}

[ordered]@{
    syncedAt = (Get-Date).ToString('o')
    status = 'ok'
    projectRoot = $projectRootPath
    sourceRoot = $sourceRoot
    previewRoot = $previewRootPath
    files = $results
} | ConvertTo-Json -Depth 4
