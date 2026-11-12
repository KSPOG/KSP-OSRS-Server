#requires -Version 5.1
<#
.SYNOPSIS
Run the pinned revision-242 JS5 server and client-branch transport diagnostic together.
.DESCRIPTION
Git-free, localhost-only, no official client/game login. Starts one disposable
PowerShell listener process and runs the standalone KspJs5ConnectionProbe source
from the client branch using Java's source-file launcher. An actual injected
gamepack is NOT launched. Preserves the existing verified master/cache gates.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
)
$ErrorActionPreference = "Stop"
$serverScriptCommit = "219cbc0a4ce59b33024210ecf9308f3f9eb6134d"
$clientCommit = "85da6d5f74b014152c89bbe9702052beeb0c1250"
$master = Join-Path $Repo "server\cache-validation\openrs2-2757\external-master-255-255.dat"
if (-not (Test-Path -LiteralPath $master -PathType Leaf)) {
    throw "Archived OpenRS2 255/255 master not found: $master"
}
$expected = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38"
if ((Get-FileHash -LiteralPath $master -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw "Archived 255/255 SHA-256 mismatch. No network listener started."
}
$existing = Get-NetTCPConnection -LocalPort 43596 -State Listen -ErrorAction SilentlyContinue
if ($existing) { throw "Port 43596 is already listening. Stop that probe before starting a new one." }

$root = Join-Path $env:TEMP ("ksp-js5-client-smoke-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $root -Force | Out-Null
$serverScript = Join-Path $root "prepare-js5-client-probe.ps1"
$clientJava = Join-Path $root "KspJs5ConnectionProbe.java"
$stdout = Join-Path $root "server-stdout.log"
$stderr = Join-Path $root "server-stderr.log"
$proc = $null
try {
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 45 -OutFile $serverScript -Uri (
        "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$serverScriptCommit/server/tools/prepare-js5-client-probe.ps1")
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 45 -OutFile $clientJava -Uri (
        "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$clientCommit/Microbot-main/runelite-client/src/main/java/net/runelite/client/plugins/microbot/KspJs5ConnectionProbe.java")
    $java = Get-Command java.exe -ErrorAction Stop
    Write-Host "Starting explicit localhost-only JS5 listener and client-branch transport diagnostic."
    $proc = Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $serverScript,
        "-Repo", $Repo, "-Listen"
    ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru

    $ready = $false
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $stdout) {
            $log = Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue
            if ($log -match "JS5_CONTROLLED_PROBE_READY") { $ready = $true; break }
        }
        $proc.Refresh()
        if ($proc.HasExited) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        Write-Host "Server output:"
        if (Test-Path $stdout) { Get-Content -LiteralPath $stdout -Tail 50 }
        if (Test-Path $stderr) { Get-Content -LiteralPath $stderr -Tail 30 }
        throw "JS5 server was not ready; no client diagnostic was sent."
    }
    Write-Host "JS5 listener ready on 127.0.0.1:43596; issuing client-branch protocol diagnostic."
    & $java.Source $clientJava $master
    if ($LASTEXITCODE -ne 0) { throw "Client-branch JS5 diagnostic failed, exit=$LASTEXITCODE" }

    # A Java response may finish before the Gradle process emits its
    # completion banner and the wrapping PowerShell process exits. Waiting
    # only 15 seconds led to a false failure after a successful TCP exchange.
    $serverFinished = $proc.WaitForExit(90000)
    $proc.Refresh()
    Write-Host "JS5 server transcript:"
    $serverLines = @()
    if (Test-Path -LiteralPath $stdout) {
        $serverLines = @(Get-Content -LiteralPath $stdout)
        $serverLines |
            Where-Object { $_ -match "^(JS5_|master_sha256=|one_session=|real_revision_242_client_acceptance=)" }
    }
    $errors = @()
    if (Test-Path -LiteralPath $stderr) {
        $errors = @(Get-Content -LiteralPath $stderr)
    }
    $report = @($serverLines | Where-Object { $_ -match "^JS5_CONTROLLED_PROBE_REPORT " })
    $sessionVerified = $false
    if ($report.Count -eq 1) {
        $line = [string]$report[0]
        $sessionVerified = ($line -match 'handshakeAccepted=true') -and
            ($line -match 'requests=3') -and
            ($line -match 'responses=2') -and
            ($line -match 'responseBytes=2939') -and
            ($line -match 'termination=CLIENT_CLOSED')
    }
    if (-not $sessionVerified) {
        Write-Host "Server tail (for diagnosis):"
        $serverLines | Select-Object -Last 35
        if ($errors.Count -gt 0) { Write-Host "Server stderr:"; $errors | Select-Object -Last 25 }
        throw "Missing successful JS5 server-side session report. Logs: $root"
    }
    if (-not $serverFinished -or -not $proc.HasExited) {
        Write-Host "Server-side protocol report is successful, but the Gradle wrapper has not exited."
        Write-Host "Server tail:"
        $serverLines | Select-Object -Last 25
        throw "Gradle launcher did not exit within 90 seconds after protocol completion. Logs: $root"
    }
    if ($proc.ExitCode -ne 0) {
        Write-Host "Server tail:"
        $serverLines | Select-Object -Last 35
        if ($errors.Count -gt 0) { Write-Host "Server stderr:"; $errors | Select-Object -Last 25 }
        throw "JS5 protocol succeeded but launcher exited with code $($proc.ExitCode). Logs: $root"
    }
    Write-Host "KSP_JS5_CROSS_BRANCH_SMOKE_PASS"
    Write-Host "This proves transport from client-branch diagnostic only, NOT injected game client acceptance."
} finally {
    if ($proc) {
        $proc.Refresh()
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
    Write-Host "Diagnostic files preserved at: $root"
}
