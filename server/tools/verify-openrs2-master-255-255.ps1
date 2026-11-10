#requires -Version 5.1
<#
.SYNOPSIS
Download a bounded independent 255/255 master-index container for OSRS revision 242.
.DESCRIPTION
Uses the OpenRS2 archive group API endpoint documented at
https://archive.openrs2.org/api . Never fabricates missing data, enables
listeners, or modifies the installed game client. Backs up changed
protocol-lab files and runs only the new master-reference tests.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$Cache = "",
    [switch]$SkipDownload
)
$ErrorActionPreference = "Stop"
$sourceCommit = "5bb5f5094d44978232706418fc097d56c4752296"
$cacheId = 2757
$revision = 242
$maxBytes = 1048576
$lab = Join-Path $Repo "server\protocol-lab"
if (-not $Cache) {
    $Cache = Join-Path $Repo "server\cache-validation\openrs2-2757\extracted\cache"
}
if (-not (Test-Path -LiteralPath (Join-Path $Cache "main_file_cache.idx255") -PathType Leaf)) {
    throw "Expected revision-242 cache not found: $Cache"
}
$referenceDir = Join-Path $Repo "server\cache-validation\openrs2-2757"
$master = Join-Path $referenceDir "external-master-255-255.dat"
$sourceUrl = "https://archive.openrs2.org/caches/runescape/$cacheId/archives/255/groups/255.dat"
New-Item -ItemType Directory -Force -Path $referenceDir | Out-Null
if ($SkipDownload) {
    if (-not (Test-Path -LiteralPath $master -PathType Leaf)) {
        throw "-SkipDownload requested but no downloaded master exists: $master"
    }
} elseif (-not (Test-Path -LiteralPath $master -PathType Leaf)) {
    $temp = "$master.download"
    if (Test-Path -LiteralPath $temp) {
        throw "Partial download exists; inspect before retry: $temp"
    }
    try {
        Write-Host "Requesting independently archived master index from: $sourceUrl"
        Invoke-WebRequest -Uri $sourceUrl -OutFile $temp -UseBasicParsing -TimeoutSec 30
        $length = (Get-Item -LiteralPath $temp).Length
        if ($length -lt 5 -or $length -gt $maxBytes) {
            throw "Unexpected master index container length: $length"
        }
        Move-Item -LiteralPath $temp -Destination $master -ErrorAction Stop
    } catch {
        if (Test-Path -LiteralPath $temp) {
            Remove-Item -LiteralPath $temp -Force
        }
        throw "Independent archive download failed; no authentic fixture was obtained. $($_.Exception.Message)"
    }
} else {
    Write-Host "Existing external master found; preserving bytes without overwrite."
}
$size = (Get-Item -LiteralPath $master).Length
if ($size -lt 5 -or $size -gt $maxBytes) {
    throw "Existing fixture invalid or oversized: $size"
}
$sha = (Get-FileHash -LiteralPath $master -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "OpenRS2 external file SHA-256: $sha"
Write-Host "Source: $sourceUrl"
Write-Host "Archive metadata: oldschool/live/en revision=$revision cache=$cacheId"
$files = @(
    "server/protocol-lab/build.gradle.kts",
    "server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/Js5ArchivedMasterReferenceAudit.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5ArchivedMasterReferenceAuditTest.java",
    "server/protocol-lab/src/test/java/dev/ksp/osrs/protocol/Js5HandshakeDiagnosticTest.java"
)
$backup = Join-Path $Repo ("js5-master-audit-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$stage = Join-Path $env:TEMP ("js5-master-source-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
try {
    foreach ($file in $files) {
        $rel = $file.Replace("/", "\")
        $target = Join-Path $stage $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        $url = "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$sourceCommit/$file"
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing -TimeoutSec 30
        if ((Get-Item -LiteralPath $target).Length -eq 0) { throw "Empty source: $file" }
    }
    foreach ($file in $files) {
        $rel = $file.Replace("/", "\")
        $target = Join-Path $Repo $rel
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            $saved = Join-Path $backup $rel
            New-Item -ItemType Directory -Force -Path (Split-Path $saved -Parent) | Out-Null
            Copy-Item -LiteralPath $target -Destination $saved
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
        Copy-Item -LiteralPath (Join-Path $stage $rel) -Destination $target -Force
    }
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
}
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$exe = if ($gradle) { $gradle.Source } else { $null }
if (-not $exe) {
    $dist = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    if (Test-Path $dist) {
        $exe = Get-ChildItem -LiteralPath $dist -File -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'gradle-8\.8[\\/]bin[\\/]gradle\.bat$' } |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $exe) { throw "Gradle 8.8 not found." }
& $exe -p $lab testJs5ArchivedMasterReferenceAudit --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Synthetic master reference tests failed." }
& $exe -p $lab auditJs5ArchivedMasterReference "-PcachePath=$Cache" "-ParchivedMasterPath=$master" --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "External master differs from local candidate or includes unverified extension. Review the audit output; file preserved at $master"
}
Write-Host "EXTERNAL_MASTER_CANDIDATE_BYTE_EXACT_MATCH"
Write-Host "This is independent content comparison, NOT evidence of signature or live-client acceptance."
