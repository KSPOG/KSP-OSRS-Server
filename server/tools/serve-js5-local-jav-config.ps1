#requires -Version 5.1
<#
.SYNOPSIS
Serve a user-reviewed local-only jav_config over loopback HTTP for an
isolated, non-authenticating Microbot JS5 gamepack diagnostic.
.DESCRIPTION
Refuses absent or nonlocal config values; does not synthesize official
auth/world metadata. GET /jav_config.ws only. The applet loader MUST
validate these values independently. No credentials or gameplay.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ConfigPath,
    [int]$Port = 43595,
    [int]$LifetimeSeconds = 1500
)
$ErrorActionPreference = 'Stop'
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid local config port' }
if ($LifetimeSeconds -lt 1 -or $LifetimeSeconds -gt 1800) { throw 'Invalid server duration' }
$resolved = (Resolve-Path -LiteralPath $ConfigPath -ErrorAction Stop).ProviderPath
$file = Get-Item -LiteralPath $resolved -ErrorAction Stop
if ($file.PSIsContainer -or $file.Length -lt 1 -or $file.Length -gt 131072) {
    throw 'Local jav_config must be a nonempty file of at most 128 KiB'
}
$body = [System.IO.File]::ReadAllText($resolved, [System.Text.Encoding]::UTF8)
if ($body -match '(?i)(?:runescape|jagex)\.(?:com|net)' -or
    $body -match '(?im)^\s*(?:param=)?[^=]*=\s*https?://(?!127\.0\.0\.1(?::|/)|localhost(?::|/))') {
    throw 'Refusing jav_config containing official or non-loopback URL values'
}
if ($body -notmatch '(?im)^codebase=http://127\.0\.0\.1:\d+/' -or
    $body -notmatch '(?im)^initial_class=client\.class\s*$' -or
    $body -notmatch '(?im)^initial_jar=\S+') {
    throw 'Local jav_config needs loopback codebase, initial_class=client.class and initial_jar'
}
$listener = [System.Net.Sockets.TcpListener]::new(
    [System.Net.IPAddress]::Parse('127.0.0.1'), $Port)
try {
    $listener.Start()
    Write-Host "KSP_LOCAL_JAV_CONFIG_READY host=127.0.0.1 port=$Port path=/jav_config.ws"
    $deadline = [DateTime]::UtcNow.AddSeconds($LifetimeSeconds)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not $listener.Pending()) {
            Start-Sleep -Milliseconds 100
            continue
        }
        $client = $listener.AcceptTcpClient()
        try {
            $client.ReceiveTimeout = 3000
            $client.SendTimeout = 3000
            $stream = $client.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII,
                $false, 1024, $true)
            $line = $reader.ReadLine()
            if ($null -eq $line) { continue }
            for ($i=0; $i -lt 40; $i++) {
                $header = $reader.ReadLine()
                if ([string]::IsNullOrEmpty($header)) { break }
            }
            $isAllowed = $line -match '^GET /jav_config\.ws(?:\?[^ ]*)? HTTP/1\.[01]$'
            $payload = if ($isAllowed) { $bytes } else {
                [System.Text.Encoding]::ASCII.GetBytes('Not Found')
            }
            $status = if ($isAllowed) { '200 OK' } else { '404 Not Found' }
            $head = [System.Text.Encoding]::ASCII.GetBytes(
                "HTTP/1.1 $status`r`nContent-Type: text/plain; charset=utf-8`r`n" +
                "Content-Length: $($payload.Length)`r`nConnection: close`r`n`r`n")
            $stream.Write($head, 0, $head.Length)
            $stream.Write($payload, 0, $payload.Length)
            $stream.Flush()
            Write-Host ("KSP_LOCAL_JAV_CONFIG_REQUEST status=" + $status.Split(' ')[0])
        } catch {
            Write-Warning ("Local config HTTP request failed: " + $_.Exception.GetType().Name)
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
    Write-Host 'KSP_LOCAL_JAV_CONFIG_STOPPED'
}
