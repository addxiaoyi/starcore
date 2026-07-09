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

function Resolve-OptionalExistingPath {
    param(
        [string]$Path
    )
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return ''
    }
    return $resolved.Path
}

function Ensure-Directory {
    param(
        [string]$Path
    )
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    return (Resolve-Path -LiteralPath $Path).Path
}

function Copy-DirectoryItem {
    param(
        [System.IO.FileSystemInfo]$Item,
        [string]$DestinationDirectory
    )
    $destination = Join-Path $DestinationDirectory $Item.Name
    if ($Item.PSIsContainer) {
        Copy-Item -LiteralPath $Item.FullName -Destination $destination -Recurse -Force
    } else {
        Copy-Item -LiteralPath $Item.FullName -Destination $destination -Force
    }
}

function Copy-DirectoryContents {
    param(
        [string]$SourceDirectory,
        [string]$DestinationDirectory
    )
    Ensure-Directory -Path $DestinationDirectory | Out-Null
    foreach ($item in Get-ChildItem -LiteralPath $SourceDirectory -Force) {
        Copy-DirectoryItem -Item $item -DestinationDirectory $DestinationDirectory
    }
}

function Get-RelativeEntries {
    param(
        [string]$BasePath
    )
    if (-not (Test-Path -LiteralPath $BasePath)) {
        return @()
    }
    $baseFullPath = [System.IO.Path]::GetFullPath($BasePath)
    if (-not $baseFullPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $baseFullPath += [System.IO.Path]::DirectorySeparatorChar
    }
    $baseUri = [System.Uri]::new($baseFullPath)
    $items = Get-ChildItem -LiteralPath $BasePath -Recurse -Force -ErrorAction SilentlyContinue
    $relative = foreach ($item in $items) {
        $itemUri = [System.Uri]::new($item.FullName)
        [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($itemUri).ToString()).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    }
    return @($relative | Sort-Object -Unique)
}

function Get-DefaultServerRoot {
    param(
        [string]$ProjectRootPath
    )
    return (Join-Path (Split-Path $ProjectRootPath -Parent) 'test-server-paper-1.21.11')
}

function Get-DefaultServerDataDir {
    param(
        [string]$ProjectRootPath
    )
    return (Join-Path (Get-DefaultServerRoot -ProjectRootPath $ProjectRootPath) 'plugins\STARCORE')
}

function Get-LatestSmokeArtifactDataDir {
    param(
        [string]$ProjectRootPath
    )
    $latest = Get-ChildItem -LiteralPath (Join-Path $ProjectRootPath 'target') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'smoke-harness-*' } |
        Sort-Object Name -Descending |
        ForEach-Object {
            $artifactDir = Join-Path $_.FullName 'STARCORE-artifact'
            if (Test-Path -LiteralPath $artifactDir) {
                return $artifactDir
            }
        } |
        Select-Object -First 1
    if (-not $latest) {
        return ''
    }
    return [System.IO.Path]::GetFullPath($latest)
}

function Get-DefaultExistingDataDir {
    param(
        [string]$ProjectRootPath
    )
    $serverDataDir = Get-DefaultServerDataDir -ProjectRootPath $ProjectRootPath
    if (Test-Path -LiteralPath $serverDataDir) {
        return [System.IO.Path]::GetFullPath($serverDataDir)
    }

    $smokeArtifactDataDir = Get-LatestSmokeArtifactDataDir -ProjectRootPath $ProjectRootPath
    if (-not [string]::IsNullOrWhiteSpace($smokeArtifactDataDir)) {
        return $smokeArtifactDataDir
    }

    throw "No existing STARCORE data directory found. Tried: $serverDataDir and target\\smoke-harness-*\\STARCORE-artifact"
}

function Read-SimpleYamlMap {
    param(
        [string]$ConfigPath
    )
    $result = @{}
    if (-not (Test-Path -LiteralPath $ConfigPath)) {
        return $result
    }

    $stack = New-Object System.Collections.ArrayList
    foreach ($rawLine in Get-Content -LiteralPath $ConfigPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($rawLine)) {
            continue
        }

        $trimmedLine = $rawLine.Trim()
        if ($trimmedLine.StartsWith('#') -or $trimmedLine.StartsWith('- ')) {
            continue
        }

        $match = [regex]::Match($rawLine, '^(?<indent>\s*)(?<key>[^:#]+):(?:\s*(?<value>.*))?$')
        if (-not $match.Success) {
            continue
        }

        $indent = $match.Groups['indent'].Value.Length
        $key = $match.Groups['key'].Value.Trim()
        $value = $match.Groups['value'].Value

        while ($stack.Count -gt 0 -and $stack[$stack.Count - 1].Indent -ge $indent) {
            $stack.RemoveAt($stack.Count - 1)
        }

        $cleanValue = $value
        if (-not [string]::IsNullOrWhiteSpace($cleanValue)) {
            $commentIndex = $cleanValue.IndexOf(' #')
            if ($commentIndex -ge 0) {
                $cleanValue = $cleanValue.Substring(0, $commentIndex)
            }
            $cleanValue = $cleanValue.Trim()
            if (($cleanValue.StartsWith('"') -and $cleanValue.EndsWith('"')) -or ($cleanValue.StartsWith("'") -and $cleanValue.EndsWith("'"))) {
                $cleanValue = $cleanValue.Substring(1, $cleanValue.Length - 2)
            }
        }

        if ([string]::IsNullOrWhiteSpace($cleanValue)) {
            [void]$stack.Add([pscustomobject]@{
                Indent = $indent
                Key = $key
            })
            continue
        }

        $pathSegments = @($stack | ForEach-Object { $_.Key }) + $key
        $path = $pathSegments -join '.'
        $result[$path] = $cleanValue
    }

    return $result
}

function Get-YamlScalarValue {
    param(
        [hashtable]$YamlMap,
        [string]$KeyPath,
        [string]$DefaultValue
    )
    if ($null -eq $YamlMap) {
        return $DefaultValue
    }
    if (-not $YamlMap.ContainsKey($KeyPath)) {
        return $DefaultValue
    }

    $value = [string]$YamlMap[$KeyPath]
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }
    return $value.Trim()
}

function Resolve-ConfiguredPath {
    param(
        [string]$BaseDirectory,
        [string]$ConfiguredPath
    )
    if ([string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        return [System.IO.Path]::GetFullPath($BaseDirectory)
    }

    $candidate = $ConfiguredPath.Trim()
    $resolved = $candidate
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $resolved = Join-Path ([System.IO.Path]::GetFullPath($BaseDirectory)) $candidate
    }
    return [System.IO.Path]::GetFullPath($resolved)
}

function Resolve-MapExportDirFromConfig {
    param(
        [string]$ConfigPath,
        [string]$DataDirBase
    )
    $yaml = Read-SimpleYamlMap -ConfigPath $ConfigPath
    $exportDirectory = Get-YamlScalarValue -YamlMap $yaml -KeyPath 'map.export-directory' -DefaultValue '..'
    $resolvedBase = Resolve-ConfiguredPath -BaseDirectory $DataDirBase -ConfiguredPath $exportDirectory
    return [System.IO.Path]::GetFullPath((Join-Path $resolvedBase 'map'))
}

function Get-LatestBackupPath {
    param(
        [string]$ProjectRootPath
    )
    $targets = Get-ChildItem -LiteralPath (Join-Path $ProjectRootPath 'target') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'runtime-backups*' }

    $candidates = @()
    foreach ($targetDir in $targets) {
        $candidates += Get-ChildItem -LiteralPath $targetDir.FullName -Force -ErrorAction SilentlyContinue |
            Where-Object {
                ($_.PSIsContainer -and $_.Name -like 'starcore-runtime-backup-*') -or
                (-not $_.PSIsContainer -and $_.Extension -eq '.zip' -and $_.BaseName -like 'starcore-runtime-backup-*')
            }
    }

    $latest = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) {
        throw 'No STARCORE runtime backup was found under target\\runtime-backups*'
    }
    return $latest.FullName
}

function ConvertTo-BooleanValue {
    param(
        [string]$Value,
        [bool]$DefaultValue
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    switch ($Value.Trim().ToLowerInvariant()) {
        'true' { return $true }
        'yes' { return $true }
        'on' { return $true }
        '1' { return $true }
        'false' { return $false }
        'no' { return $false }
        'off' { return $false }
        '0' { return $false }
        default { return $DefaultValue }
    }
}

function Test-TcpEndpoint {
    param(
        [string]$EndpointHost,
        [int]$Port,
        [int]$TimeoutMs
    )
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($EndpointHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            $client.Close()
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}
