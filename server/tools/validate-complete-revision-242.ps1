#requires -Version 5.1
<#
.SYNOPSIS
 Install and verify the offline revision-242 JS5 exhaustive audit, without Git.
.DESCRIPTION
 Downloads pinned sources including required handshake tests into an existing checkout,
 backs up any replaced files, then runs synthetic tests and read-only full-cache
 audits against an isolated, user-specified cache. Does not start listeners.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$Cache = "",
    [switch]$VerifyProvider,
    [switch]$Quick
)
$ErrorActionPreference = "Stop"
$commit = "7d3c272d085854ac9b56e241e2c6cb2b376183c6"
$lab = Join-Path $Repo "server\protocol-lab"
if (-not (Test-Path -LiteralPath (Join-Path $lab "build.gradle.kts") -PathType Leaf)) {
    throw "Protocol-lab Gradle project missing from $lab"
}
if (-not $Cache) {
    $Cache = Join-Path $Repo "server\cache-validation\openrs2-2757\extracted\cache"
}
if (-not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.idx255") -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.dat2") -PathType Leaf)) {
    throw "A readable isolated JS5 cache was not found at $Cache"
}
$files = @(
    "server/protocol-lab/build.gradle.kts",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5ExhaustiveCacheAudit.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5ExhaustiveCacheAuditTest.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5HandshakePayload.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5HandshakePayloadTest.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5OfflineExchange.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5OfflineExchangeTest.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/VerifiedJs5GroupProvider.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/VerifiedJs5GroupProviderTest.java",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5MasterWireFixtureAudit.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5MasterWireFixtureAuditTest.java"
)
$staging = Join-Path $env:TEMP ("ksp-js5-stage-" + [guid]::NewGuid().ToString("N"))
$backup = Join-Path $Repo ("js5-exhaustive-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $staging, $backup | Out-Null
try {
    foreach ($file in $files) {
        $relative = $file.Replace("/", "\")
        $destination = Join-Path $staging $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        $url = "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$commit/$file"
        Invoke-WebRequest -Uri $url -OutFile $destination -UseBasicParsing
        if ((Get-Item -LiteralPath $destination).Length -le 0) {
            throw "Empty download: $file"
        }
        Write-Host "Downloaded: $file"
    }
    foreach ($file in $files) {
        $relative = $file.Replace("/", "\")
        $destination = Join-Path $Repo $relative
        if (Test-Path -LiteralPath $destination) {
            $saved = Join-Path $backup $relative
            New-Item -ItemType Directory -Force -Path (Split-Path $saved -Parent) | Out-Null
            Copy-Item -LiteralPath $destination -Destination $saved -Force
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        Copy-Item -LiteralPath (Join-Path $staging $relative) -Destination $destination -Force
        Write-Host "Installed: $file"
    }
} finally {
    Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "Previous file backups: $backup"
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$executable = if ($gradle) { $gradle.Source } else { $null }
if (-not $executable) {
    $dists = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    if (Test-Path $dists) {
        $executable = Get-ChildItem -Path $dists -File -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'gradle-8\.8[\\/]bin[\\/]gradle\.bat$' } |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $executable) { throw "Gradle 8.8 not found" }
& $executable -p $lab testExhaustiveCacheAudit testJs5HandshakePayload testJs5Provider testJs5OfflineExchange testJs5MasterWireFixtureAudit --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Synthetic verification failed" }
& $executable -p $lab auditRevision242Metadata "-PcachePath=$Cache" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Revision-242 metadata comparison failed" }
if (-not $Quick) {
    $arguments = @("-p", $lab, "auditAllJs5Groups", "-PcachePath=$Cache", "--no-daemon")
    if ($VerifyProvider) { $arguments += "-PverifyProvider=true" }
    & $executable @arguments
    if ($LASTEXITCODE -ne 0) { throw "Exhaustive JS5 validation failed" }
} else {
    Write-Host "Quick mode: skipping previously validated exhaustive group scan"
}

& $executable -p $lab auditJs5OfflineExchange "-PcachePath=$Cache" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Offline RSProt-242 request exchange failed" }
Write-Host "Offline checks passed. JS5 255/255 and client compatibility remain UNVERIFIED."
