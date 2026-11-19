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
$files = @('LocalGameListener.java')
foreach ($file in $files) {
    $path = Join-Path $src $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Source is missing: $path" }
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing server JAR: $jar" }
if (-not (Test-Path -LiteralPath $lib -PathType Container)) { throw "Missing RSProt libraries: $lib" }
if (-not (Get-ChildItem -LiteralPath $lib -Filter 'compression-*.jar' -File -ErrorAction SilentlyContinue)) {
    throw "RSProt dependencies incomplete; expected compression-*.jar in $lib"
}
$bin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($bin -and (Test-Path (Join-Path $bin 'javac.exe'))) {
    Join-Path $bin 'javac.exe'
} else { (Get-Command javac.exe -ErrorAction Stop).Source }
$jarTool = if ($bin -and (Test-Path (Join-Path $bin 'jar.exe'))) {
    Join-Path $bin 'jar.exe'
} else { (Get-Command jar.exe -ErrorAction Stop).Source }
$target = Join-Path $release '.ksp-tutorial-build'
$classes = Join-Path $target 'classes'
if (Test-Path $classes) { Remove-Item $classes -Force -Recurse }
New-Item $classes -ItemType Directory -Force | Out-Null
$classPath = "$jar;$lib\*;$release\lib\*"
$javaFiles = @($files | ForEach-Object { Join-Path $src $_ })
Write-Host 'Compiling Tutorial Island guide changes against installed revision 242 dependencies...'
& $javac --release 21 -encoding UTF-8 -cp $classPath -d $classes @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; the original JAR was not modified.' }
$compiled = Join-Path $classes 'dev\ksp\osrs\protocol\LocalGameListener.class'
if (-not (Test-Path $compiled -PathType Leaf)) { throw 'Expected class not generated.' }
$staged = Join-Path $target 'world-server-next.jar'
Copy-Item $jar $staged -Force
& $jarTool uf $staged -C $classes 'dev/ksp/osrs/protocol'
if ($LASTEXITCODE -ne 0) { throw 'Could not update staged JAR.' }
$backup = Join-Path $release 'world-server-before-object-routing-fix.jar.bak'
if (-not (Test-Path $backup)) { Copy-Item $jar $backup -Force }
Copy-Item $staged $jar -Force
Write-Host 'KSP_OBJECT_ROUTING_FIX_INSTALLED' -ForegroundColor Green
Write-Host 'Other scene objects still require independent gameplay handlers.' -ForegroundColor Yellow
