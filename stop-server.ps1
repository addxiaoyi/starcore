$ErrorActionPreference = 'SilentlyContinue'
$port = 25566
$cp = New-Object System.Net.Sockets.TcpClient
try {
    $cp.Connect('127.0.0.1', $port)
    $sw = New-Object System.IO.StreamWriter($cp.GetStream())
    $sw.WriteLine('stop')
    $sw.Flush()
    Start-Sleep -Seconds 3
    $cp.Close()
    Write-Host 'Sent stop to server on port' $port
} catch {
    Write-Host 'RCON/connect failed:' $_.Exception.Message
}
$cp.Close()
