@echo off
setlocal
cd /d "%~dp0.."
set "KSP_OSRS_LOG_REVISION=true"
set "KSP_OSRS_PRIVATE_SERVER="
set "KSP_OSRS_PRIVATE_JAV_CONFIG_URL="
set "KSP_OSRS_DISCOVERY_URL="
set "KSP_OSRS_WORLDS_URL="
echo [KSP] Starting normal Microbot to record injected OSRS client revision.
java -version 2>nul
if errorlevel 1 (
    echo [KSP] ERROR: Java 21 JDK is required on PATH.
    exit /b 1
)
javac -version 2>nul
if errorlevel 1 (
    echo [KSP] ERROR: javac is unavailable. Install or configure a JDK 21, not only a JRE.
    exit /b 1
)
echo [KSP] Private-server routing is disabled for this diagnostic.
echo [KSP] Look for: KSP_OSRS_REVISION ^| revision=... buildId=... privateProfile=false
call gradlew.bat :client:run --no-daemon
if errorlevel 1 (
    echo [KSP] Microbot launch failed. Check the build log.
    exit /b 1
)
