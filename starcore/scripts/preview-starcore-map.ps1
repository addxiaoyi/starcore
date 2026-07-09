[CmdletBinding()]
param(
    [int]$Port = 43123,
    [switch]$OpenBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-PortAvailable {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse('127.0.0.1'), $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($listener -ne $null) {
            $listener.Stop()
        }
    }
}

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse('127.0.0.1'), 0)
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Resolve-PythonExecutable {
    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if ($pythonCommand -and $pythonCommand.Source) {
        return $pythonCommand.Source
    }

    $pyCommand = Get-Command py -ErrorAction SilentlyContinue
    if ($pyCommand -and $pyCommand.Source) {
        $pythonPath = & $pyCommand.Source -c "import sys; print(sys.executable)" 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($pythonPath)) {
            return $pythonPath.Trim()
        }
    }

    throw 'No Python runtime found. Install Python or ensure python/py is on PATH.'
}

function Wait-ListeningProcess {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$Process,
        [int]$TimeoutMilliseconds = 5000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) {
            return $false
        }
        $listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
        if ($listening -contains $Process.Id) {
            return $true
        }
        Start-Sleep -Milliseconds 150
    }
    return $false
}

$mapRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\src\main\resources\web\map')).Path
$indexPath = Join-Path $mapRoot 'index.html'
if (-not (Test-Path -LiteralPath $indexPath)) {
    throw "Map entry not found: $indexPath"
}

$selectedPort = if (Test-PortAvailable -Port $Port) { $Port } else { Get-FreePort }
$pythonExecutable = Resolve-PythonExecutable
$process = Start-Process -WindowStyle Hidden -FilePath $pythonExecutable -ArgumentList '-m', 'http.server', $selectedPort, '--bind', '127.0.0.1' -WorkingDirectory $mapRoot -PassThru
$url = "http://127.0.0.1:$selectedPort/?demo=1"

if (-not (Wait-ListeningProcess -Port $selectedPort -Process $process)) {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    throw "Preview server failed to start on 127.0.0.1:$selectedPort"
}

if ($OpenBrowser) {
    Start-Process $url | Out-Null
}

Write-Host ''
Write-Host 'STARCORE map local preview started.' -ForegroundColor Green
Write-Host "Map root: $mapRoot"
Write-Host "Preview URL: $url"
Write-Host "Process PID: $($process.Id)"
Write-Host ''
Write-Host 'Stop preview example:'
Write-Host "Stop-Process -Id $($process.Id)"
