#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Work = (Join-Path $env:USERPROFILE 'Downloads\KSP-Microbot-242-Test'),
    [switch]$Launch,
    [ValidateRange(1,2000)][int]$World = 301
)
$ErrorActionPreference = 'Stop'
$commit = '648400f43453144614ca2aa9c96e3b798542aaab'
$folder = 'KSP-OSRS-Server-' + $commit
$destinationRoot = Join-Path $Work $folder
$client = Join-Path $destinationRoot 'Microbot-main'
$gradlew = Join-Path $client 'gradlew.bat'
$release = $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $release 'client-overrides\manifest.json') -Raw | ConvertFrom-Json

function Assert-ClientFiles([string]$clientPath) {
    if (-not (Test-Path -LiteralPath (Join-Path $clientPath 'gradlew.bat') -PathType Leaf)) {
        throw "Microbot Gradle wrapper missing from $clientPath"
    }
    $sourceRoot = Join-Path $clientPath 'runelite-client\src\main\java'
    foreach ($entry in $manifest) {
        $target = Join-Path $sourceRoot $entry.path
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "Required client source missing: $target"
        }
        $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        if (($actual -ne $entry.original) -and ($actual -ne $entry.updated)) {
            throw "Pinned client source hash mismatch: $target ($actual)"
        }
    }
}

if (Test-Path -LiteralPath $gradlew -PathType Leaf) {
    Assert-ClientFiles $client
    Write-Host 'KSP_CLIENT_SOURCE_ALREADY_READY' -ForegroundColor Green
} else {
    if (Test-Path -LiteralPath $destinationRoot) {
        throw "Destination exists but is incomplete: $destinationRoot. Back it up or move it aside manually before restoring."
    }
    New-Item -ItemType Directory -Path $Work -Force | Out-Null
    $scratch = Join-Path $Work ('ksp-client-restore-' + [Guid]::NewGuid().ToString('N'))
    $archive = Join-Path $scratch 'pinned-source.zip'
    $unpacked = Join-Path $scratch 'unpacked'
    New-Item -ItemType Directory -Path $scratch,$unpacked -Force | Out-Null
    try {
        $url = "https://github.com/KSPOG/KSP-OSRS-Server/archive/$commit.zip"
        Write-Host "Downloading pinned GitHub source commit $commit ..."
        Invoke-WebRequest -Uri $url -UseBasicParsing -OutFile $archive -ErrorAction Stop
        Write-Host 'Extracting pinned source ...'
        Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force -ErrorAction Stop
        $root = Get-ChildItem -LiteralPath $unpacked -Directory | Select-Object -First 1
        if (-not $root) { throw 'GitHub source archive did not contain a root directory.' }
        $downloadedClient = Join-Path $root.FullName 'Microbot-main'
        Assert-ClientFiles $downloadedClient
        # Move the entire pinned repository folder into the exact layout the historical launcher expects.
        Move-Item -LiteralPath $root.FullName -Destination $destinationRoot -ErrorAction Stop
        Write-Host 'KSP_CLIENT_PINNED_SOURCE_RESTORED' -ForegroundColor Green
    } finally {
        if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Force -Recurse -ErrorAction SilentlyContinue }
    }
}

Write-Host "Verified client source: $client"
Write-Host 'The original launcher will verify and apply its three bundled overrides on launch.'
Write-Host 'Requires Java 21, the pinned injected-client gamepack in Gradle cache, local RSA public key and a running local game server.'
if ($Launch) {
    $launcher = Join-Path $release 'start-local-microbot.ps1'
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) { throw "Launcher missing: $launcher" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -Work $Work -World $World
    if ($LASTEXITCODE -ne 0) { throw "Client launcher exited with code $LASTEXITCODE" }
}
