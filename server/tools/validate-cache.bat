@echo off
setlocal
set "ROOT=%~dp0.."
set "LAB=%ROOT%\protocol-lab"
set "SRC=%LAB%\src\main\java\dev\ksp\osrs\protocol"
set "BUILD=%LAB%\build\cache-check"
if "%~1"=="" (
    echo Usage: validate-cache.bat "C:\path\to\jagexcache\oldschool\LIVE"
    exit /b 2
)
where javac >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java 21 JDK compiler not found on PATH.
    exit /b 1
)
if not exist "%BUILD%" mkdir "%BUILD%"
javac --release 21 -d "%BUILD%" "%SRC%\IndexedCacheReader.java" "%SRC%\CacheStructureCheck.java"
if errorlevel 1 exit /b 1
java -cp "%BUILD%" dev.ksp.osrs.protocol.CacheStructureCheck "%~1"
exit /b %errorlevel%
