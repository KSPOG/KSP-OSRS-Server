#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$config = Join-Path $root "jav_config.local.template.ws"
$serve = Join-Path $root "serve-js5-local-jav-config.ps1"
$port = 49831
$out = Join-Path $env:TEMP ("ksp-local-config-" + [guid]::NewGuid().ToString("N") + ".log")
$err = $out + ".err"
$engine = (Get-Process -Id $PID).Path
$proc = $null
try {
    $proc = Start-Process -FilePath $engine -ArgumentList @(
        '-NoProfile', '-File', $serve, '-ConfigPath', $config,
        '-Port', $port, '-LifetimeSeconds', 15
    ) -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $ready = $false
    for ($i = 0; $i -lt 80; $i++) {
        if (Test-Path -LiteralPath $out) {
            if ((Get-Content -LiteralPath $out -Raw -ErrorAction SilentlyContinue) -match 'KSP_LOCAL_JAV_CONFIG_READY') {
                $ready = $true; break
            }
        }
        $proc.Refresh()
        if ($proc.HasExited) { break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) {
        if (Test-Path $err) { Get-Content $err -Tail 20 }
        throw 'Local HTTP fixture server did not become ready'
    }
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/jav_config.ws" -UseBasicParsing -TimeoutSec 5
    $validHttp = ($response.StatusCode -eq 200)
    $hasClass = ($response.Content -match 'initial_class=client.class')
    $unsafeOrigin = ($response.Content -match '(?i)jagex.com|runescape.com')
    if (-not $validHttp -or -not $hasClass -or $unsafeOrigin) {
        throw 'Unexpected local jav_config response'
    }
    $notFound = $false
    try { Invoke-WebRequest -Uri "http://127.0.0.1:$port/unrelated" -UseBasicParsing -TimeoutSec 5 | Out-Null }
    catch { $notFound = $true }
    if (-not $notFound) { throw 'Unexpected route served by local fixture' }
    Write-Host 'KSP_JS5_LOCAL_CONFIG_HTTP_TEST_PASS'
} finally {
    if ($proc) {
        $proc.Refresh()
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
    Remove-Item -LiteralPath $out,$err -Force -ErrorAction SilentlyContinue
}
