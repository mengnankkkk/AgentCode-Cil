@echo off
REM HarmonySafeAgent Windows Launcher
REM Usage: agent-safe [options]

setlocal enabledelayedexpansion

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%..\target\harmony-agent.jar"

REM Check if JAR exists
if not exist "%JAR_PATH%" (
    echo Error: harmony-agent.jar not found at %JAR_PATH%
    echo Please run: mvn clean package
    exit /b 1
)

REM Launch the application
java -jar "%JAR_PATH%" %*
