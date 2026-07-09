param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryJsonPath,
    [string]$OutputPath = ''
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

function Format-SqliteCounts {
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

$summaryPath = Resolve-RequiredPath -Path $SummaryJsonPath -Label 'Smoke summary JSON'
$summary = Get-Content -LiteralPath $summaryPath -Raw -Encoding UTF8 | ConvertFrom-Json

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add(('Smoke summary source: `{0}`' -f $summaryPath))
$lines.Add('')
$lines.Add(('- Health: ``{0}``' -f $summary.health))
$lines.Add(('- Marker: ``{0}``' -f $summary.marker))
if ($summary.markerLine) {
    $lines.Add(('- Marker line: ``{0}``' -f $summary.markerLine))
}
if ($summary.webClaimSmoke) {
    $lines.Add(('- Web claim smoke: ``{0}`` (`{1}`)' -f $summary.webClaimSmoke.status, $summary.webClaimSmoke.details))
}
if ($summary.browserSmoke) {
    $lines.Add(('- Browser smoke: ``{0}`` (`{1}`)' -f $summary.browserSmoke.status, $summary.browserSmoke.details))
}
if ($summary.browserDom) {
    $lines.Add(('- Browser DOM: ``{0}``' -f $summary.browserDom))
}
if ($summary.browserScreenshot) {
    $lines.Add(('- Browser screenshot: ``{0}``' -f $summary.browserScreenshot))
}
if ($summary.browserMobileScreenshot) {
    $lines.Add(('- Browser mobile screenshot: ``{0}``' -f $summary.browserMobileScreenshot))
}
if ($summary.outLog) {
    $lines.Add(('- Paper log: ``{0}``' -f $summary.outLog))
}
if ($summary.harness -and $summary.harness.message) {
    $lines.Add(('- Harness message: ``{0}``' -f $summary.harness.message))
}
$sqliteCountsLine = Format-SqliteCounts -Counts $summary.harness.sqliteCounts
if ($sqliteCountsLine) {
    $lines.Add(('- SQLite counts: {0}' -f $sqliteCountsLine))
}

$markdown = $lines -join [Environment]::NewLine

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $markdown
} else {
    $target = [System.IO.Path]::GetFullPath($OutputPath)
    Set-Content -LiteralPath $target -Value $markdown -Encoding UTF8
    $markdown
}
