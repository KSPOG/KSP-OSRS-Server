#requires -Version 5.1
[CmdletBinding()]
param(
  [string]$Install = (Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Local-Game-Windows')
)
$ErrorActionPreference = 'Stop'
$cache = Join-Path $Install 'revision-242\cache'
$release = $PSScriptRoot
$src = Join-Path $release 'widget-audit\TutorialWidgetAudit.java'
$jar = Join-Path $release 'world-server.jar'
$libs = Join-Path $Install 'lib'
if (-not (Test-Path -LiteralPath (Join-Path $cache 'main_file_cache.dat2'))) { throw 'Revision 242 cache missing' }
$javac = (Get-Command javac.exe -ErrorAction Stop).Source
$java = (Get-Command java.exe -ErrorAction Stop).Source
$classes = Join-Path $release '.widget-audit-build'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$cp = "$jar;$libs\*;$release\lib\*"
& $javac --release 21 -encoding UTF-8 -cp $cp -d $classes $src
if ($LASTEXITCODE -ne 0) { throw 'Widget audit compile failed' }
$out = Join-Path $release 'tutorial-viewport-widgets.tsv'
& $java -cp "$classes;$cp" dev.ksp.osrs.protocol.TutorialWidgetAudit $cache $out
if ($LASTEXITCODE -ne 0) { throw 'Widget audit failed' }
Write-Host "Widget layout report: $out" -ForegroundColor Green
