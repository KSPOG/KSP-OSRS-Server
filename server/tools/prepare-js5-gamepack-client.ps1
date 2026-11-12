#requires -Version 5.1
<#
.SYNOPSIS
Prepare the existing Microbot rev-242 CLIENT branch for an experimental JS5 gamepack probe.
.DESCRIPTION
Fetches an isolated GitHub client branch snapshot when needed, installs only
the pinned applet probe source changes, compiles the client, and optionally
starts a one-session 127.0.0.1:43596 JS5 listener plus the *actual* client.
No account credentials, official login automation, or private-ready bypass.
#>
[CmdletBinding()]
param(
    [string]$Repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server",
    [string]$ClientRoot = "",
    [int]$Port = 43594,
    [int]$LocalConfigPort = 43595,
    [string]$LocalJavConfigPath = "",
    [switch]$LaunchClient
)
$ErrorActionPreference = "Stop"
$clientCommit = "23036abc82ccafb96c758847ca3737c77c9f5e1e"
$serverCommit = "8b224b264ca18dab1997aef7b1b3aa22fe161fd6"
if ($Port -lt 1 -or $Port -gt 65535) { throw "Port must be between 1 and 65535." }
if ($LocalConfigPort -lt 1 -or $LocalConfigPort -gt 65535 -or $LocalConfigPort -eq $Port) {
    throw "Local jav_config port must be valid and different from JS5 port"
}
if (-not $ClientRoot) {
    $ClientRoot = Join-Path $Repo "client-gamepack-probe\Microbot-main"
}
$clientParent = Split-Path -Parent $ClientRoot
$archive = Join-Path $env:TEMP "ksp-client-branch-$clientCommit.zip"
$stage = Join-Path $env:TEMP ("ksp-client-stage-" + [guid]::NewGuid().ToString("N"))
$master = Join-Path $Repo "server\cache-validation\openrs2-2757\external-master-255-255.dat"
$expectedMaster = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38"
$files = @(
    "runelite-client/build.gradle.kts",
    "runelite-client/src/main/java/net/runelite/client/RuneLite.java",
    "runelite-client/src/main/java/net/runelite/client/plugins/microbot/KspJs5GamepackProbeConfig.java",
    "runelite-client/src/main/java/net/runelite/client/plugins/microbot/KspJs5LoopbackGuard.java",
    "runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotRSAppletStub.java",
    "runelite-client/src/main/java/net/runelite/client/rs/RSAppletStub.java",
    "runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotClientLoader.java",
    "runelite-client/src/main/java/net/runelite/client/rs/ClientLoader.java",
    "runelite-client/src/test/java/net/runelite/client/plugins/microbot/KspJs5GamepackProbeConfigSmokeTest.java"
)
if (-not (Test-Path -LiteralPath (Join-Path $Repo "server\protocol-lab") -PathType Container)) {
    throw "Server protocol-lab not found: $Repo"
}
if (-not (Test-Path -LiteralPath $master -PathType Leaf)) {
    throw "Archived OpenRS2 255/255 fixture not found: $master"
}
if ((Get-FileHash -LiteralPath $master -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedMaster) {
    throw "Independent OpenRS2 master SHA-256 mismatch"
}
if (-not (Test-Path -LiteralPath (Join-Path $ClientRoot "gradlew.bat") -PathType Leaf)) {
    Write-Host "Preparing isolated client branch checkout (may be a large download)."
    $zipUrl = "https://github.com/KSPOG/KSP-OSRS-Server/archive/$clientCommit.zip"
    Invoke-WebRequest -Uri $zipUrl -UseBasicParsing -OutFile $archive -TimeoutSec 180
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    try {
        Expand-Archive -LiteralPath $archive -DestinationPath $stage -Force
        $source = Get-ChildItem -LiteralPath $stage -Directory |
            Select-Object -First 1
        if (-not $source -or -not (Test-Path -LiteralPath (Join-Path $source.FullName "Microbot-main\gradlew.bat"))) {
            throw "Downloaded client snapshot does not contain the expected Microbot tree"
        }
        New-Item -ItemType Directory -Force -Path $clientParent | Out-Null
        Move-Item -LiteralPath (Join-Path $source.FullName "Microbot-main") -Destination $ClientRoot
    } finally {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$backups = Join-Path $ClientRoot ("ksp-js5-probe-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
foreach ($file in $files) {
    $relative = $file.Replace("/", "\")
    $dst = Join-Path $ClientRoot $relative
    $src = "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$clientCommit/Microbot-main/$file"
    $temp = Join-Path $env:TEMP ("js5-client-source-" + [guid]::NewGuid().ToString("N"))
    try {
        Invoke-WebRequest -Uri $src -UseBasicParsing -OutFile $temp -TimeoutSec 45
        if ((Get-Item -LiteralPath $temp).Length -eq 0) {
            throw "Empty source file: $file"
        }
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            $saved = Join-Path $backups $relative
            New-Item -ItemType Directory -Force -Path (Split-Path $saved -Parent) | Out-Null
            Copy-Item -LiteralPath $dst -Destination $saved -Force
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $dst -Parent) | Out-Null
        Copy-Item -LiteralPath $temp -Destination $dst -Force
    } finally {
        Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "Client source at: $ClientRoot"
Write-Host "Previous source backups: $backups"
Push-Location $ClientRoot
try {
    & (Join-Path $ClientRoot "gradlew.bat") :client:compileJava --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Microbot gamepack probe client compilation failed"
    }
} finally {
    Pop-Location
}
Write-Host "KSP_JS5_GAMEPACK_CLIENT_COMPILE_PASS"
Write-Host "KSP_JS5_NEXT_PHASE launchRequested=$([bool]$LaunchClient) port=$Port"
Write-Host "Injected gamepack JS5 endpoint consumption: NOT YET VERIFIED"
if (-not $LaunchClient) {
    Write-Host "No network listener or game client launched. Re-run with -LaunchClient when ready."
    return
}
Write-Host "KSP_JS5_PORT_CHECK port=$Port"
if ((Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) -or
    (Get-NetTCPConnection -LocalPort $LocalConfigPort -State Listen -ErrorAction SilentlyContinue)) {
    throw "JS5 or local jav_config port is already in use; stop the earlier test first"
}
$runRoot = Join-Path $env:TEMP ("ksp-gamepack-probe-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $runRoot | Out-Null
$serverScript = Join-Path $runRoot "prepare-js5-client-probe.ps1"
$localScript = Join-Path $runRoot "serve-js5-local-jav-config.ps1"
$localConfig = if ($LocalJavConfigPath) { $LocalJavConfigPath } else { Join-Path $runRoot "jav_config.local.template.ws" }
$localOut = Join-Path $runRoot "local-config.log"
$localErr = Join-Path $runRoot "local-config.err"
$launchScript = Join-Path $runRoot "launch-client.ps1"
$serverOut = Join-Path $runRoot "server.log"
$serverErr = Join-Path $runRoot "server.err"
Write-Host "KSP_JS5_SERVER_PREP downloadingPinnedProbe=true"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$serverCommit/server/tools/prepare-js5-client-probe.ps1" -OutFile $serverScript -UseBasicParsing -TimeoutSec 45
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$serverCommit/server/tools/serve-js5-local-jav-config.ps1" -OutFile $localScript -UseBasicParsing -TimeoutSec 45
if (-not $LocalJavConfigPath) {
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/KSPOG/KSP-OSRS-Server/$serverCommit/server/tools/jav_config.local.template.ws" -OutFile $localConfig -UseBasicParsing -TimeoutSec 45
    Write-Warning "Using minimal unverified local configuration template. Only JS5 transport can be tested; game startup/login is not expected to work."
}
$launcher = @'
param([string]$ClientRoot, [string]$LogPath, [string]$ProfileRoot, [int]$Port, [int]$LocalConfigPort)
Set-Location -LiteralPath $ClientRoot
Write-Host 'KSP gamepack probe: isolated client profile, no automated authentication.'
Write-Host 'Do not enter account credentials.'
& (Join-Path $ClientRoot 'gradlew.bat') :client:runKspJs5GamepackProbe "-PkspJs5ProbePort=$Port" "-PkspJs5ProbeHome=$ProfileRoot" "-PkspJs5LocalConfigUrl=http://127.0.0.1:$LocalConfigPort/jav_config.ws" --no-daemon *>&1 |
    Tee-Object -FilePath $LogPath
'@
Set-Content -LiteralPath $launchScript -Value $launcher -Encoding UTF8
$serverProc = $null
$localProc = $null
try {
    Write-Host "KSP_LOCAL_JAV_CONFIG_START port=$LocalConfigPort file=$localConfig"
    $localProc = Start-Process -FilePath "powershell.exe" -PassThru -WindowStyle Hidden -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $localScript,
        "-ConfigPath", $localConfig, "-Port", $LocalConfigPort
    ) -RedirectStandardOutput $localOut -RedirectStandardError $localErr
    $localReady = $false
    $localDeadline = (Get-Date).AddSeconds(12)
    while ((Get-Date) -lt $localDeadline) {
        if (Test-Path -LiteralPath $localOut) {
            if ((Get-Content -LiteralPath $localOut -Raw -ErrorAction SilentlyContinue) -match "KSP_LOCAL_JAV_CONFIG_READY") {
                $localReady = $true; break
            }
        }
        $localProc.Refresh()
        if ($localProc.HasExited) { break }
        Start-Sleep -Milliseconds 150
    }
    if (-not $localReady) {
        if (Test-Path -LiteralPath $localErr) { Get-Content -LiteralPath $localErr -Tail 25 }
        throw "Loopback jav_config service failed to start; refusing gamepack test"
    }
    Write-Host "KSP_LOCAL_JAV_CONFIG_READY noOfficialConfigFallback=true"

    Write-Host "KSP_JS5_SERVER_START port=$Port"
    $serverProc = Start-Process -FilePath "powershell.exe" -PassThru -WindowStyle Hidden -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $serverScript,
        "-Repo", $Repo, "-Port", $Port, "-Listen", "-ExtendedClientSession"
    ) -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
    $ready = $false
    $until = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $until) {
        if (Test-Path -LiteralPath $serverOut) {
            $lines = Get-Content -LiteralPath $serverOut -Raw -ErrorAction SilentlyContinue
            if ($lines -match "JS5_CONTROLLED_PROBE_READY") {
                $ready = $true
                break
            }
        }
        $serverProc.Refresh()
        if ($serverProc.HasExited) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        if (Test-Path -LiteralPath $serverOut) {
            Get-Content -LiteralPath $serverOut -Tail 30
        }
        throw "Server was not ready; game client was not started"
    }
    Write-Host "JS5 pinned master server ready. Launching real injected game client in its own PowerShell window."
    Write-Host "Do not log in; this is a JS5-only diagnostic."
    $clientLog = Join-Path $runRoot "client.log"
    $clientProc = Start-Process -FilePath "powershell.exe" -PassThru -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-NoExit", "-File", $launchScript,
        "-ClientRoot", $ClientRoot, "-LogPath", $clientLog,
        "-ProfileRoot", (Join-Path $runRoot "isolated-client-home"), "-Port", $Port,
        "-LocalConfigPort", $LocalConfigPort
    )
    Write-Host "KSP_JS5_CLIENT_LAUNCH_REQUESTED processId=$($clientProc.Id) isolatedProfile=true"
    $until = (Get-Date).AddMinutes(22)
    $nextNotice = (Get-Date).AddSeconds(20)
    $sawHandshake = $false
    while ((Get-Date) -lt $until) {
        $serverProc.Refresh()
        if (Test-Path -LiteralPath $serverOut) {
            $serverText = Get-Content -LiteralPath $serverOut -Raw -ErrorAction SilentlyContinue
            if (-not $sawHandshake -and $serverText -match 'JS5_HANDSHAKE_ACCEPTED') {
                $sawHandshake = $true
                Write-Host 'JS5 handshake observed on the localhost listener.'
            }
        }
        if ($serverProc.HasExited) { break }
        if ((Get-Date) -ge $nextNotice) {
            Write-Host "JS5 probe active: gamepack handshake observed=$sawHandshake; listening window is bounded."
            $nextNotice = (Get-Date).AddSeconds(20)
        }
        Start-Sleep -Seconds 1
    }
    Write-Host "Server JS5 evidence:"
    $serverText = ""
    if (Test-Path -LiteralPath $serverOut) {
        $serverText = Get-Content -LiteralPath $serverOut -Raw
        $serverText -split '\r?\n' |
            Where-Object { $_ -match "^JS5_(CONTROLLED_PROBE|REQUEST|RESPONSE|HANDSHAKE|NO_CLIENT|CLIENT_CONNECTION)" }
    }
    if ($serverText -match "JS5_HANDSHAKE_ACCEPTED") {
        Write-Host "KSP_JS5_GAMEPACK_SOCKET_OBSERVED: review client logs to establish the originating process."
    } else {
        Write-Host "KSP_JS5_GAMEPACK_SOCKET_NOT_OBSERVED: endpoint-hint routing remains unverified."
    }
    if (Test-Path -LiteralPath $clientLog) {
        $clientLines = @(Get-Content -LiteralPath $clientLog)
        Write-Host "Client JS5/GPU evidence:"
        $clientLines | Where-Object { $_ -match "KSP_JS5_GPU_DISABLED|GpuPlugin - Using device|error_game_js5connect|KSP_JS5_GAMEPACK|BUILD FAILED" } | Select-Object -Last 45
        if ($clientLines -match "GpuPlugin - Using device") {
            Write-Warning "GPU was initialized despite the isolated safe-mode setting; inspect client log."
        }
    }
    Write-Host "Client diagnostic log: $clientLog"
    Write-Host "Look for KSP_JS5_GAMEPACK_CODEBASE and KSP_JS5_GAMEPACK_PARAM in the client log."
    Write-Host "Client acceptance, successful cache loading and world login remain UNVERIFIED until tested."
} finally {
    if ($serverProc) {
        $serverProc.Refresh()
        if (-not $serverProc.HasExited) {
            Stop-Process -Id $serverProc.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($localProc) {
        $localProc.Refresh()
        if (-not $localProc.HasExited) {
            Stop-Process -Id $localProc.Id -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Host "Full captured server logs: $runRoot"
}
