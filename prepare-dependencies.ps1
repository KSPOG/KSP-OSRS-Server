#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Install = (Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Local-Game-Windows')
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$java = 'java.exe'
if ($env:JAVA_HOME) {
    $javaHomeBin = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaHomeBin -PathType Leaf) { $java = $javaHomeBin }
}
& $java --version
if ($LASTEXITCODE -ne 0) { throw 'Install JDK 21 and configure JAVA_HOME first.' }

$cacheRoot = Join-Path $Install 'revision-242'
$cache = Join-Path $cacheRoot 'cache'
$cacheFiles = @(
    (Join-Path $cache 'main_file_cache.dat2')
    (Join-Path $cache 'main_file_cache.idx255')
)
New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
if ($cacheFiles.Where({ -not (Test-Path -LiteralPath $_ -PathType Leaf) }).Count -gt 0) {
    $download = Join-Path $Install 'openrs2-2757.zip'
    $partial = "$download.partial"
    Invoke-WebRequest -UseBasicParsing -Uri 'https://archive.openrs2.org/caches/runescape/2757/disk.zip' -OutFile $partial
    Move-Item -LiteralPath $partial -Destination $download -Force
    Expand-Archive -LiteralPath $download -DestinationPath $cacheRoot -Force
    foreach ($cacheFile in $cacheFiles) {
        if (-not (Test-Path -LiteralPath $cacheFile -PathType Leaf)) {
            throw "Revision 242 cache file missing after extraction: $cacheFile"
        }
    }
}

$master = Join-Path $Install 'master-255-255.dat'
$expected = 'a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38'
if (-not (Test-Path -LiteralPath $master -PathType Leaf) -or
    (Get-FileHash -LiteralPath $master -Algorithm SHA256).Hash -ne $expected) {
    $partial = "$master.partial"
    Invoke-WebRequest -UseBasicParsing -Uri 'https://archive.openrs2.org/caches/runescape/2757/archives/255/groups/255.dat' -OutFile $partial
    if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $expected) {
        throw 'The revision 242 master index checksum did not match.'
    }
    Move-Item -LiteralPath $partial -Destination $master -Force
}

$keys = Join-Path $env:USERPROFILE 'ksp-rsps-local-keys'
if (-not (Test-Path -LiteralPath $keys)) {
    $runtime = Join-Path $PSScriptRoot 'runtime'
    $classpath = "$(Join-Path $runtime 'local-game.jar');$(Join-Path $runtime 'lib\*')"
    & $java -cp $classpath dev.ksp.osrs.protocol.LocalGameListener --generate-keypair $keys
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate the local key pair.' }
}
foreach ($keyFile in 'local-private.pem', 'local-public.pem') {
    if (-not (Test-Path -LiteralPath (Join-Path $keys $keyFile) -PathType Leaf)) {
        throw "The existing local key folder is missing $keyFile. Restore the matching pair before continuing."
    }
}

Push-Location -LiteralPath (Join-Path $PSScriptRoot 'Microbot-main')
try {
    .\gradlew.bat :client:classes :client:primeKspJs5RuntimeClasspath --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle could not prepare the revision 242 client runtime.' }
} finally {
    Pop-Location
}
Write-Host 'KSP_CLIENT_AND_SERVER_DEPENDENCIES_READY'
