@echo off
setlocal
cd /d "%~dp0.."
set "KSP_OSRS_LOG_REVISION=true"
set "KSP_OSRS_PRIVATE_SERVER="
set "KSP_OSRS_PRIVATE_JAV_CONFIG_URL="
set "KSP_OSRS_DISCOVERY_URL="
set "KSP_OSRS_WORLDS_URL="
echo [KSP] Starting normal Microbot to record injected OSRS client revision.
echo [KSP] Private-server routing is disabled for this diagnostic.
echo [KSP] Look for: KSP_OSRS_REVISION ^| revision=... buildId=... privateProfile=false
call gradlew.bat :client:run --no-daemon
if errorlevel 1 (
    echo [KSP] Microbot launch failed. Check the build log.
    exit /b 1
)
