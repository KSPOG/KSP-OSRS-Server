@echo off
setlocal
cd /d "%~dp0.."
if not exist build\classes mkdir build\classes
javac -encoding UTF-8 -d build\classes src\main\java\dev\ksp\osrs\ServerMain.java
if errorlevel 1 exit /b 1
java -cp build\classes dev.ksp.osrs.ServerMain config\worlds.csv
