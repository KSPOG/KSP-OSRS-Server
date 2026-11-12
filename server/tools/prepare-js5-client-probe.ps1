#requires -Version 5.1
<#
.SYNOPSIS
Prepare and validate the pinned OpenRS2 revision-242 localhost client probe.
.DESCRIPTION
PowerShell 5.1, Git-free; does not start a listener unless -Listen is passed.
Downloads only current probe sources, backing up overwritten local files.
Reads the OpenRS2 cache and master reference; never modifies client files.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$Cache = "",
    [string]$ArchivedMaster = "",
    [int]$Port = 43596,
    [switch]$Listen,
    [switch]$ExtendedClientSession
)
$ErrorActionPreference = "Stop"
$commit = "1b352242d81571fe18674eff3c5632ce6aaa22a6"
$lab = Join-Path $Repo "server\protocol-lab"
if (-not $Cache) {
    $Cache = Join-Path $Repo "server\cache-validation\openrs2-2757\extracted\cache"
}
if (-not $ArchivedMaster) {
    $ArchivedMaster = Join-Path $Repo "server\cache-validation\openrs2-2757\external-master-255-255.dat"
}
if ($Port -lt 1 -or $Port -gt 65535) { throw "Port must be 1..65535." }
if (-not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.idx255") -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.dat2") -PathType Leaf)) {
    throw "Revision-242 cache is not available at $Cache"
}
if (-not (Test-Path -LiteralPath $ArchivedMaster -PathType Leaf)) {
    throw "Independent OpenRS2 master file missing: $ArchivedMaster"
}
$expected = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38"
$actual = (Get-FileHash -LiteralPath $ArchivedMaster -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) {
    throw "Pinned OpenRS2 master SHA-256 mismatch; refusing to prepare listener."
}
$files = @(
    "server/protocol-lab/build.gradle.kts",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5HandshakeDiagnosticServer.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5VerifiedMasterProbe.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5VerifiedMasterProbeAudit.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5VerifiedMasterProbeTest.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5ProbeAcceptTimeoutTest.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5ExtendedSessionTest.java"
)
$stage = Join-Path $env:TEMP ("ksp-js5-probe-stage-" + [guid]::NewGuid().ToString("N"))
$backup = Join-Path $Repo ("js5-probe-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
try {
    foreach ($file in $files) {
        $relative = $file.Replace("/", "\")
        $target = Join-Path $stage $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        $url = "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$commit/$file"
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing -TimeoutSec 45
        if ((Get-Item -LiteralPath $target).Length -le 0) { throw "Empty source: $file" }
    }
    foreach ($file in $files) {
        $relative = $file.Replace("/", "\")
        $target = Join-Path $Repo $relative
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            $saved = Join-Path $backup $relative
            New-Item -ItemType Directory -Force -Path (Split-Path $saved -Parent) | Out-Null
            Copy-Item -LiteralPath $target -Destination $saved -Force
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        Copy-Item -LiteralPath (Join-Path $stage $relative) -Destination $target -Force
    }
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "Updated probe source. Previous files backed up to $backup"
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$exe = if ($gradle) { $gradle.Source } else { $null }
if (-not $exe) {
    $dist = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    if (Test-Path -LiteralPath $dist) {
        $exe = Get-ChildItem -LiteralPath $dist -Recurse -File -Filter gradle.bat -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'gradle-8\.8[\\/]bin[\\/]gradle\.bat$' } |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $exe) { throw "Gradle 8.8 not found." }
& $exe -p $lab testJs5VerifiedMasterProbe testJs5ProbeAcceptTimeout testJs5ExtendedSession --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Synthetic controlled probe validation failed" }
& $exe -p $lab auditJs5VerifiedMasterProbe "-PcachePath=$Cache" "-ParchivedMasterPath=$ArchivedMaster" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Real-cache verified-master socket audit failed" }
Write-Host "VERIFIED_MASTER_PROBE_PREFLIGHT_PASSED"
if ($Listen) {
    Write-Host "Explicit localhost-only session; client JS5 endpoint must point to 127.0.0.1:$Port"
    Write-Host "Accept timeout 180s, request idle timeout 7s; one connection only."
    if ($ExtendedClientSession) {
        Write-Host "Extended native gamepack session: max 8192 requests, max session 10 minutes."
    }
    $extended = if ($ExtendedClientSession) { "true" } else { "false" }
    & $exe -p $lab runJs5VerifiedMasterProbe "-PcachePath=$Cache" "-ParchivedMasterPath=$ArchivedMaster" "-PprobePort=$Port" "-PconfirmLocalProbe=true" "-PextendedJs5Session=$extended" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Controlled probe failed; review console output" }
} else {
    Write-Host "No listener started. Pass -Listen only when ready for a controlled local client connection."
}
