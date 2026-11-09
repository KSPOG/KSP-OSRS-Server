@echo off
setlocal
cd /d "%~dp0.."
if "%KSP_ADMIN_TOKEN%"=="" (
  echo ERROR: KSP_ADMIN_TOKEN is required. Set a secure token in PowerShell before launching.
  exit /b 1
)
java -cp ksp-osrs-coordinator.jar dev.ksp.osrs.ServerMain config\worlds.csv
if errorlevel 1 (
  echo Coordinator failed. Check Java 21 and the configuration.
  exit /b 1
)
