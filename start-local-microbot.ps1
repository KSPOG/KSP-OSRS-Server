#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Work = (Join-Path $env:USERPROFILE 'Downloads\KSP-Microbot-242-Test'),
    [ValidateRange(1, 2000)][int]$World = 301,
    [switch]$SafeMode,
    [string]$PublicKey = (Join-Path $env:USERPROFILE 'ksp-rsps-local-keys\local-public.pem')
)

$ErrorActionPreference = 'Stop'
$clientCommit = '648400f43453144614ca2aa9c96e3b798542aaab'
$client = Join-Path $Work "KSP-OSRS-Server-$clientCommit\Microbot-main"
$bundledClient = Join-Path $PSScriptRoot 'Microbot-main'
if (Test-Path -LiteralPath (Join-Path $bundledClient 'gradlew.bat') -PathType Leaf) {
    $client = $bundledClient
}
$launcher = Join-Path $client 'gradlew.bat'
foreach ($required in @($launcher, $PublicKey)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required file not found: $required"
    }
}

$configUrl = "http://127.0.0.1:8087/client/jav_config.ws?world=$World"
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $configUrl -TimeoutSec 3
    if ($response.Content -notmatch "(?m)^param=12=$World\r?$") {
        throw 'The returned applet configuration has the wrong world.'
    }
} catch {
    throw "Cannot get the local configuration for world $World. Start it at http://127.0.0.1:8087/admin."
}

$overrides = Join-Path $PSScriptRoot 'client-overrides'
$sourceRoot = Join-Path $client 'runelite-client\src\main\java'
$manifest = Get-Content -LiteralPath (Join-Path $overrides 'manifest.json') -Raw | ConvertFrom-Json
$patches = foreach ($entry in $manifest) {
    $sourceFile = Join-Path $overrides $entry.path
    $target = Join-Path $sourceRoot $entry.path
    if ((Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash -ne $entry.updated) {
        throw "Update file failed verification: $sourceFile"
    }
    $found = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
    if ($found -eq $entry.updated) { continue }
    if ($found -ne $entry.original) {
        throw "The local source differs from the pinned version: $target"
    }
    [pscustomobject]@{ source = $sourceFile; target = $target }
}
foreach ($patch in $patches) {
    $backup = $patch.target + '.before-local-worlds.bak'
    if (-not (Test-Path -LiteralPath $backup)) {
        Copy-Item -LiteralPath $patch.target -Destination $backup
    }
    Copy-Item -LiteralPath $patch.source -Destination $patch.target -Force
}

$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else {
    Join-Path $env:USERPROFILE '.gradle'
}
$artifacts = Join-Path $gradleHome 'caches\modules-2\files-2.1\net.runelite\injected-client\1.13.6'
$gamepackHash = '6a9dc96d6b0870f1cc95b47f18be554685245d4606347925cb47d900e49c6255'
$gamepack = Get-ChildItem -LiteralPath $artifacts -Filter 'injected-client-1.13.6.jar' -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash -eq $gamepackHash } |
    Select-Object -First 1
if (-not $gamepack) {
    throw 'The pinned gamepack was not found in Gradle. Run prepare-dependencies.ps1 with Java 21 first.'
}

$java = 'java.exe'
if ($env:JAVA_HOME) {
    $javaHomeBin = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaHomeBin -PathType Leaf) { $java = $javaHomeBin }
}
$local = Join-Path $Work 'local-login'
New-Item -ItemType Directory -Path $local -Force -ErrorAction Stop | Out-Null
$patched = Join-Path $local 'injected-client-local-world-play.jar'
$patcher = Join-Path $PSScriptRoot 'local-login-key-patcher.jar'
$asm = Join-Path $PSScriptRoot 'asm-9.7.jar'
& $java -cp "$patcher;$asm" LocalLoginKeyPatcher $gamepack.FullName $PublicKey $patched --local-worlds
if ($LASTEXITCODE -ne 0) { throw 'The localhost RSA key could not be installed into the isolated gamepack.' }

$init = Join-Path $PSScriptRoot 'local-login.init.gradle'
$homePath = Join-Path $local 'profile'
$safe = $SafeMode.IsPresent.ToString().ToLowerInvariant()
$launchArgs = @('-I', $init, ':client:runKspLocalGameLogin', "-PkspLocalGamepack=$patched",
    "-PkspLocalGameHome=$homePath", "-PkspLocalWorld=$World", "-PkspLocalSafeMode=$safe", '--offline', '--no-daemon')
Push-Location -LiteralPath $client -ErrorAction Stop
try {
    & $launcher @launchArgs 2>&1 | Tee-Object -FilePath (Join-Path $local 'client-login.log')
    if ($LASTEXITCODE -ne 0) { throw 'Microbot local login launch ended with an error.' }
} finally {
    Pop-Location
}
