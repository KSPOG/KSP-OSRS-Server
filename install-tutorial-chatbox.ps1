#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Install = (Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Local-Game-Windows')
)
$ErrorActionPreference = 'Stop'
$release = $PSScriptRoot
$jar = Join-Path $release 'world-server.jar'
$src = Join-Path $release 'source\server\protocol-lab\src\main\java\dev\ksp\osrs\protocol'
$lib = Join-Path $Install 'lib'
$files = @('LocalGameListener.java', 'NewPlayerProfile.java', 'LocalBank.java', 'LumbridgeNpcs.java', 'TutorialIslandMilestones.java')

foreach ($file in $files) {
    $path = Join-Path $src $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required source missing: $path" }
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Server JAR missing: $jar" }
if (-not (Test-Path -LiteralPath $lib -PathType Container)) { throw "RSProt libraries missing: $lib" }
if (-not (Get-ChildItem -LiteralPath $lib -Filter 'compression-*.jar' -File -ErrorAction SilentlyContinue)) {
    throw 'RSProt runtime incomplete; compression JAR missing. Restore your RSProt Gradle dependencies.'
}
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($javaBin -and (Test-Path -LiteralPath (Join-Path $javaBin 'javac.exe'))) {
    Join-Path $javaBin 'javac.exe'
} else { (Get-Command javac.exe -ErrorAction Stop).Source }
$jarExe = if ($javaBin -and (Test-Path -LiteralPath (Join-Path $javaBin 'jar.exe'))) {
    Join-Path $javaBin 'jar.exe'
} else { (Get-Command jar.exe -ErrorAction Stop).Source }

# Keep the compiled classes private to the release; never edit originals on a failed build.
$build = Join-Path $release '.tutorial-chatbox-build'
$classes = Join-Path $build 'classes'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Force -Recurse }
New-Item -Path $classes -ItemType Directory -Force | Out-Null
$classPath = "$jar;$lib\*;$release\lib\*"
$javaSources = @($files | ForEach-Object { Join-Path $src $_ })
Write-Host 'Compiling matched Tutorial Island classes against RSProt revision 242...'
& $javac --release 21 -encoding UTF-8 -cp $classPath -d $classes @javaSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed. Existing server JAR has NOT been changed.' }
foreach ($type in @('LocalGameListener', 'NewPlayerProfile', 'LocalBank')) {
    $required = Join-Path $classes ("dev\ksp\osrs\protocol\$type.class")
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing compiled class: $type" }
}
$stagedJar = Join-Path $build 'world-server-tutorial-chatbox-next.jar'
Copy-Item -LiteralPath $jar -Destination $stagedJar -Force
& $jarExe uf $stagedJar -C $classes 'dev/ksp/osrs/protocol'
if ($LASTEXITCODE -ne 0) { throw 'Could not update staged JAR. Original JAR is intact.' }
$jarFiles = @(& $jarExe tf $stagedJar)
if ($LASTEXITCODE -ne 0 -or $jarFiles -notcontains 'dev/ksp/osrs/protocol/LocalGameListener.class') {
    throw 'Staged JAR verification failed. Original JAR is intact.'
}
$backup = Join-Path $build ('world-server-before-chatbox-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.jar.bak')
Copy-Item -LiteralPath $jar -Destination $backup -Force
Copy-Item -LiteralPath $stagedJar -Destination $jar -Force
Write-Host 'KSP_TUTORIAL_CHATBOX_INSTALLED' -ForegroundColor Green
Write-Host ('Backup: ' + $backup)
Write-Host 'Start the server and verify animations and rewards in the actual client.' -ForegroundColor Yellow
