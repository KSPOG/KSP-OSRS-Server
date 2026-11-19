#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Install = (Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Local-Game-Windows'),
    [switch]$FreeAccount,
    [switch]$EnableWorld999
)

$ErrorActionPreference = 'Stop'
$cache = Join-Path $Install 'revision-242\cache'
$master = Join-Path $Install 'master-255-255.dat'
$key = Join-Path $env:USERPROFILE 'ksp-rsps-local-keys\local-private.pem'
$dependencyDir = Join-Path $PSScriptRoot 'runtime\lib'
if (-not (Test-Path -LiteralPath $dependencyDir -PathType Container)) {
    $dependencyDir = Join-Path $Install 'lib'
}
foreach ($path in @($master, $key, (Join-Path $cache 'main_file_cache.dat2'),
        (Join-Path $cache 'main_file_cache.idx255'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file missing: $path" }
}
if (-not (Get-ChildItem -LiteralPath $dependencyDir -Filter 'osrs-242-api-*.jar' -File)) {
    throw "The existing revision 242 game installation is missing its libraries: $dependencyDir"
}
if (Get-NetTCPConnection -State Listen -LocalPort 8087,43301,43302,43306,43308,43999 -ErrorAction SilentlyContinue) {
    throw 'Stop the previous world coordinator or local world processes first.'
}
$java = 'java.exe'
if ($env:JAVA_HOME) {
    $javaHomeBin = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaHomeBin -PathType Leaf) { $java = $javaHomeBin }
}

$environment = @{}
$names = @('KSP_ADMIN_TOKEN','KSP_LOCAL_LOGIN_USERNAME','KSP_LOCAL_LOGIN_PASSWORD',
    'KSP_LOCAL_LOGIN_MEMBER','KSP_ENABLE_WORLD_999','KSP_PLAYER_DATA_DIR','KSP_WORLD_STATE_FILE')
foreach ($name in $names) { $environment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

$tokenBytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($tokenBytes) } finally { $random.Dispose() }
$token = [Convert]::ToBase64String($tokenBytes)
try {
    $password = Read-Host 'Enter your disposable password for localtest' -AsSecureString
    if ($password.Length -eq 0) { throw 'A disposable password is required.' }
    $env:KSP_LOCAL_LOGIN_USERNAME = 'localtest'
    $env:KSP_LOCAL_LOGIN_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    $env:KSP_LOCAL_LOGIN_MEMBER = (-not $FreeAccount.IsPresent).ToString().ToLowerInvariant()
    $env:KSP_ENABLE_WORLD_999 = $EnableWorld999.IsPresent.ToString().ToLowerInvariant()
    $env:KSP_ADMIN_TOKEN = $token
    $data = Join-Path $Install 'data'
    $env:KSP_PLAYER_DATA_DIR = $data
    $env:KSP_WORLD_STATE_FILE = Join-Path $data 'world-states.properties'
    $jar = Join-Path $PSScriptRoot 'world-server.jar'
    $classpath = "$jar;$dependencyDir\*;$PSScriptRoot\lib\*"
    Push-Location -LiteralPath $PSScriptRoot
    try {
        Write-Host "Dashboard: http://127.0.0.1:8087/admin"
        Write-Host "Admin token: $token"
        & $java -cp $classpath dev.ksp.osrs.ServerMain '.\config\worlds.csv' $cache $master $key
        if ($LASTEXITCODE -ne 0) { throw "The world coordinator exited with code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $environment[$name], 'Process') }
}
