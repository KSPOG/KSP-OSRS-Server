#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Install = (Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Local-Game-Windows')
)
$ErrorActionPreference = 'Stop'

$release = $PSScriptRoot
$serverJar = Join-Path $release 'world-server.jar'
$dependencies = Join-Path $Install 'lib'
$serverSource = Join-Path $release 'source\server\protocol-lab\src\main\java\dev\ksp\osrs\protocol'

foreach ($file in @($serverJar, (Join-Path $serverSource 'LocalBank.java'),
    (Join-Path $serverSource 'LocalGameListener.java'))) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing required file: $file" }
}
if (-not (Test-Path -LiteralPath $dependencies -PathType Container)) {
    throw "Missing runtime library folder: $dependencies. Restore the RSProt runtime dependencies first."
}
$missing = Get-ChildItem -LiteralPath $dependencies -Filter 'compression-*.jar' -File -ErrorAction SilentlyContinue
if (-not $missing) {
    throw "RSProt compression JAR not installed in $dependencies. Run your Gradle dependency-bootstrap installation first."
}

$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($javaBin -and (Test-Path -LiteralPath (Join-Path $javaBin 'javac.exe'))) {
    Join-Path $javaBin 'javac.exe'
} else { (Get-Command javac.exe -ErrorAction Stop).Source }
$jarTool = if ($javaBin -and (Test-Path -LiteralPath (Join-Path $javaBin 'jar.exe'))) {
    Join-Path $javaBin 'jar.exe'
} else { (Get-Command jar.exe -ErrorAction Stop).Source }

$build = Join-Path $release '.ksp-live-editor-build'
$classes = Join-Path $build 'classes'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Path $classes -Force | Out-Null

# The Java server coordinator and API are already compiled into this release.
# Compile ONLY the two patched game-world classes against the exact installed RSProt runtime.
$classPath = "$serverJar;$dependencies\*;$release\lib\*"
Write-Host 'Compiling live gameplay container synchronization against RSProt revision 242...'
& $javac --release 21 -encoding UTF-8 -cp $classPath -d $classes `
    (Join-Path $serverSource 'LocalBank.java') `
    (Join-Path $serverSource 'LocalGameListener.java')
if ($LASTEXITCODE -ne 0) { throw 'Game-world compilation failed. Original world-server.jar has not been changed.' }

$gameClass = Join-Path $classes 'dev\ksp\osrs\protocol\LocalGameListener.class'
$bankClass = Join-Path $classes 'dev\ksp\osrs\protocol\LocalBank.class'
foreach ($required in @($gameClass,$bankClass)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing compiled class: $required" }
}

$temporaryJar = Join-Path $build 'world-server-patched.jar'
Copy-Item -LiteralPath $serverJar -Destination $temporaryJar -Force
Push-Location $classes
try {
    & $jarTool uf $temporaryJar 'dev/ksp/osrs/protocol/LocalGameListener.class' `
        'dev/ksp/osrs/protocol/LocalBank.class'
    if ($LASTEXITCODE -ne 0) { throw 'Could not add patched classes to the JAR.' }
} finally { Pop-Location }

$backup = Join-Path $release 'world-server-before-live-editor.jar.bak'
if (-not (Test-Path -LiteralPath $backup)) {
    Copy-Item -LiteralPath $serverJar -Destination $backup -ErrorAction Stop
}
Copy-Item -LiteralPath $temporaryJar -Destination $serverJar -Force
Write-Host 'KSP_LIVE_EDITOR_INSTALLED' -ForegroundColor Green
Write-Host 'Dashboard: http://127.0.0.1:8087/admin/bank'
Write-Host 'Now start the world coordinator using start-worlds.ps1.'
