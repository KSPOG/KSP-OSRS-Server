#requires -Version 5.1
<#
.SYNOPSIS
 Synchronize the pinned read-only RSProt 242 JS5 laboratory and test local cache.
.DESCRIPTION
 Downloads audited Java source (no cache assets) into the chosen repository.
 Does not modify the user's OSRS/RuneLite cache and does not start a server.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$Cache = "C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE"
)

$ErrorActionPreference = "Stop"
$commit = "2500bbdc1fed07ca630afad24f8d24b82e01a6d9"
$lab = Join-Path $Repo "server\protocol-lab"
$directory = Join-Path $lab "src\main\java\dev\ksp\osrs\protocol"
if (-not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.dat2"))) {
    throw "Game cache not found. Supply -Cache 'C:\path\to\LIVE'."
}
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$base = "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$commit/server/protocol-lab"
$names = @(
    "IndexedCacheReader.java",
    "CacheStructureCheck.java",
    "CacheMetadata.java",
    "CacheIntegrityAudit.java",
    "Js5WireFraming.java",
    "VerifiedJs5GroupProvider.java",
    "Js5ProviderAudit.java",
    "Js5MasterIndexCandidate.java"
)
foreach ($name in $names) {
    Invoke-WebRequest "$base/src/main/java/dev/ksp/osrs/protocol/$name" -OutFile (Join-Path $directory $name) -UseBasicParsing
}
Invoke-WebRequest "$base/build.gradle.kts" -OutFile (Join-Path $lab "build.gradle.kts") -UseBasicParsing

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
if (-not $executable) { throw "Gradle 8.8 not found. Install Gradle or provide it on PATH." }
Write-Host "Using Gradle: $executable"
Write-Host "Running local-only JS5 provider validation (network service stays disabled)."
& $executable -p $lab auditJs5Provider "-PcachePath=$Cache" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Local JS5 provider audit failed (exit $LASTEXITCODE)." }
Write-Host "Local JS5 provider audit completed. Revision 242 cache compatibility remains unverified."
