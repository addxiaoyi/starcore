# RCON client script for Minecraft
param(
    [string]$Host = "localhost",
    [int]$Port = 25575,
    [string]$Password = "starcore123",
    [string]$Command = "dbmigrate run"
)

# Convert password to bytes (padded to 128 bytes)
$passwordBytes = [System.Text.Encoding]::UTF8.GetBytes($Password)
$packetPassword = New-Object byte[] 128
[Array]::Copy($passwordBytes, $packetPassword, [Math]::Min($passwordBytes.Length, 128))

# Create TCP client
$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Host, $Port)
$stream = $client.GetStream()

# Build RCON packet: packetType=2 (EXECUTE_COMMAND), payload = command string
$commandBytes = [System.Text.Encoding]::UTF8.GetBytes($Command)
$payloadLength = $commandBytes.Length + 10  # command + 2 bytes padding
$payload = New-Object byte[] $payloadLength
$payload[0] = 0x04  # Request ID (int32)
$payload[1] = 0x00
$payload[2] = 0x00
$payload[3] = 0x00
$payload[4] = 0x02  # Packet type 2 = EXECUTE_COMMAND
$payload[5] = 0x00  # Password start (will copy below)
# ... actually let's use a simpler approach

# Actually let me use a simpler approach with raw bytes
$requestId = [BitConverter]::GetBytes([int]42)
$packetType = [BitConverter]::GetBytes([int]2)

# Build complete packet: length + requestId + packetType + payload + null terminator
$packet = New-Object System.Collections.ArrayList
$nullTerm = [byte[]](0,0)  # Two null bytes between sections

# Calculate payload: command bytes
$cmdBytes = [System.Text.Encoding]::UTF8.GetBytes($Command)
$packet.AddRange($nullTerm)  # packet type
$packet.AddRange($cmdBytes)  # command
$packet.AddRange($nullTerm)  # padding

$bodyLength = $requestId.Length + $packetType.Length + $cmdBytes.Length + 4
$lengthBytes = [BitConverter]::GetBytes([int]$bodyLength)

# Final packet: length + requestId + packetType + cmd + nulls
$fullPacket = New-Object System.Collections.ArrayList
$fullPacket.AddRange($lengthBytes)
$fullPacket.AddRange($requestId)
$fullPacket.AddRange($packetType)
$fullPacket.AddRange($cmdBytes)
$fullPacket.AddRange($nullTerm)

$stream.Write($fullPacket.ToArray(), 0, $fullPacket.Count)
$stream.Flush()

# Read response
Start-Sleep -Milliseconds 100
$buffer = New-Object byte[] 4096
$bytesRead = $stream.Read($buffer, 0, $buffer.Length)
$response = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $bytesRead)
Write-Host "Response: $response"

$client.Close()
