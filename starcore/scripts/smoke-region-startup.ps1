# Region module startup smoke (ASCII-only to avoid PS codepage issues).
# Boots Paper headless with the freshly built jar, waits for "Done (", then stops.
# Verification of Chinese log markers is done separately via the Grep tool on the saved log.
param(
    [int]$StartupTimeoutSeconds = 180,
    [int]$PostDoneGraceSeconds = 8
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$serverRoot  = Join-Path (Split-Path $projectRoot -Parent) 'test-server-paper-1.21.11'
$paperJar    = Join-Path $serverRoot 'paper-1.21.11-69.jar'
$freshJar    = Join-Path $projectRoot 'target\starcore-0.1.0-SNAPSHOT.jar'
$pluginJar   = Join-Path $serverRoot 'plugins\starcore-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path $paperJar))  { throw "Paper jar not found: $paperJar" }
if (-not (Test-Path $freshJar))  { throw "Build artifact not found: $freshJar" }

Copy-Item -LiteralPath $freshJar -Destination $pluginJar -Force
Write-Host "[1/4] Deployed fresh jar to plugins/"

$stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog = Join-Path $serverRoot "zcode-region-smoke-$stamp.out.log"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName  = 'java'
$psi.Arguments = '-Xms1G -Xmx2G -jar "' + $paperJar + '" --nogui'
$psi.WorkingDirectory       = $serverRoot
$psi.UseShellExecute        = $false
$psi.RedirectStandardInput  = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError  = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.StandardErrorEncoding  = [System.Text.Encoding]::UTF8

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi

$sb = New-Object System.Text.StringBuilder
$onData = {
    if ($EventArgs.Data) { [void]$Event.MessageData.AppendLine($EventArgs.Data) }
}
$outSub = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action $onData -MessageData $sb
$errSub = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived  -Action $onData -MessageData $sb

Write-Host "[2/4] Starting Paper server (headless)..."
[void]$proc.Start()
$proc.BeginOutputReadLine()
$proc.BeginErrorReadLine()

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$started = $false
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) { break }
    if ($sb.ToString() -match 'Done \(') { $started = $true; break }
    Start-Sleep -Milliseconds 500
}

if ($started) {
    Write-Host "[3/4] Startup complete; collecting module logs for $PostDoneGraceSeconds s..."
    Start-Sleep -Seconds $PostDoneGraceSeconds
} else {
    Write-Host "[3/4] Did not detect 'Done' within timeout; stopping..."
}

try { $proc.StandardInput.WriteLine('stop'); $proc.StandardInput.Flush() } catch {}
if (-not $proc.WaitForExit(60000)) {
    Write-Host "stop timed out; killing process"
    try { $proc.Kill() } catch {}
}

Start-Sleep -Milliseconds 500
Unregister-Event -SourceIdentifier $outSub.Name -ErrorAction SilentlyContinue
Unregister-Event -SourceIdentifier $errSub.Name -ErrorAction SilentlyContinue

$log = $sb.ToString()
Set-Content -LiteralPath $outLog -Value $log -Encoding UTF8

Write-Host "[4/4] Log saved: $outLog"
if ($started) { Write-Host "STARTUP_STATE=DONE" } else { Write-Host "STARTUP_STATE=NO_DONE" }
Write-Host "LOG_PATH=$outLog"
