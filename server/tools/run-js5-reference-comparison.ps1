#requires -Version 5.1
<#
.SYNOPSIS
 Runs a read-only, offline comparison of the local JS5 master-index candidate
 against an independently obtained reference container.
.DESCRIPTION
 Requires both a reference file and an explicit SHA-256 pin. Does not download
 assets, modify the OSRS cache, or start any network listener.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$Cache = "C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE",
    [Parameter(Mandatory = $true)][string]$Reference,
    [Parameter(Mandatory = $true)][string]$ReferenceSha256
)
$ErrorActionPreference = "Stop"
if ($ReferenceSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw "ReferenceSha256 must be 64 hex digits" }
if (-not (Test-Path -LiteralPath $Reference -PathType Leaf)) { throw "Reference fixture not found" }
if (-not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.dat2") -PathType Leaf)) {
    throw "Cache not found"
}
$lab = Join-Path $Repo "server\protocol-lab"
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
& $executable -p $lab testJs5MasterReferenceComparator compareJs5MasterReference `
    "-PcachePath=$Cache" "-PmasterReferencePath=$Reference" `
    "-PmasterReferenceSha256=$ReferenceSha256" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Master-index reference verification failed (exit $LASTEXITCODE)" }
Write-Host "Pinned fixture matched local candidate byte-for-byte. Client acceptance still UNVERIFIED."
