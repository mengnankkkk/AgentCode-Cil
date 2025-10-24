@echo off
REM HarmonySafeAgent Docker Runner Script for Windows
REM Provides convenient commands for running the Docker container

setlocal enabledelayedexpansion

REM Configuration
set IMAGE_NAME=harmony-safe-agent
set CONTAINER_NAME=harmony-agent
set VERSION=latest

REM Helper functions
:log_info
echo [INFO] %~1
goto :eof

:log_success
echo [SUCCESS] %~1
goto :eof

:log_warning
echo [WARNING] %~1
goto :eof

:log_error
echo [ERROR] %~1
goto :eof

REM Check if Docker is running
:check_docker
docker info >nul 2>&1
if errorlevel 1 (
    call :log_error "Docker is not running. Please start Docker first."
    exit /b 1
)
goto :eof

REM Build the Docker image
:build
call :log_info "Building HarmonySafeAgent Docker image..."
docker build -t %IMAGE_NAME%:%VERSION% .
if errorlevel 1 (
    call :log_error "Failed to build Docker image"
    exit /b 1
)
call :log_success "Docker image built successfully: %IMAGE_NAME%:%VERSION%"
goto :eof

REM Run interactive mode
:interactive
call :log_info "Starting HarmonySafeAgent in interactive mode..."

REM Create necessary directories
if not exist workspace mkdir workspace
if not exist reports mkdir reports
if not exist cache mkdir cache
if not exist config mkdir config

REM Check for .env file
if not exist .env (
    call :log_warning ".env file not found. Creating from .env.example..."
    copy .env.example .env >nul
    call :log_warning "Please edit .env file with your API keys before running."
)

docker run -it --rm ^
    --name %CONTAINER_NAME% ^
    --env-file .env ^
    -v "%cd%\workspace:/app/workspace:ro" ^
    -v "%cd%\reports:/app/reports" ^
    -v "%cd%\cache:/app/cache" ^
    -v "%cd%\config:/app/config" ^
    %IMAGE_NAME%:%VERSION%
goto :eof

REM Run analysis on a specific path
:analyze
set source_path=%~2
set output_file=%~3

if "%source_path%"=="" (
    call :log_error "Usage: %0 analyze <source_path> [output_file]"
    exit /b 1
)

if not exist "%source_path%" (
    call :log_error "Source path does not exist: %source_path%"
    exit /b 1
)

call :log_info "Analyzing: %source_path%"

REM Prepare arguments
set args=analyze /app/workspace/%~nx2
if not "%output_file%"=="" (
    set args=!args! -o /app/reports/%output_file%
)

REM Create necessary directories
if not exist reports mkdir reports
if not exist cache mkdir cache

docker run --rm ^
    --env-file .env ^
    -v "%source_path%:/app/workspace/%~nx2:ro" ^
    -v "%cd%\reports:/app/reports" ^
    -v "%cd%\cache:/app/cache" ^
    %IMAGE_NAME%:%VERSION% !args!
goto :eof

REM Run strategic analysis
:strategic
set source_path=%~2

if "%source_path%"=="" (
    call :log_error "Usage: %0 strategic <source_path>"
    exit /b 1
)

if not exist "%source_path%" (
    call :log_error "Source path does not exist: %source_path%"
    exit /b 1
)

call :log_info "Running strategic analysis on: %source_path%"

REM Create necessary directories
if not exist reports mkdir reports
if not exist cache mkdir cache

docker run -it --rm ^
    --env-file .env ^
    -v "%source_path%:/app/workspace/%~nx2:ro" ^
    -v "%cd%\reports:/app/reports" ^
    -v "%cd%\cache:/app/cache" ^
    %IMAGE_NAME%:%VERSION% strategic-analyze "/app/workspace/%~nx2"
goto :eof

REM Show container logs
:logs
docker logs -f %CONTAINER_NAME%
goto :eof

REM Stop and remove container
:stop
call :log_info "Stopping HarmonySafeAgent container..."
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm %CONTAINER_NAME% >nul 2>&1
call :log_success "Container stopped and removed"
goto :eof

REM Clean up Docker resources
:clean
call :log_info "Cleaning up Docker resources..."
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm %CONTAINER_NAME% >nul 2>&1
docker rmi %IMAGE_NAME%:%VERSION% >nul 2>&1
call :log_success "Cleanup completed"
goto :eof

REM Show help
:help
echo HarmonySafeAgent Docker Runner for Windows
echo.
echo Usage: %0 ^<command^> [arguments]
echo.
echo Commands:
echo   build                     Build the Docker image
echo   interactive               Start interactive mode
echo   analyze ^<path^> [output]   Analyze source code
echo   strategic ^<path^>          Run strategic analysis
echo   logs                      Show container logs
echo   stop                      Stop and remove container
echo   clean                     Clean up Docker resources
echo   help                      Show this help message
echo.
echo Examples:
echo   %0 build
echo   %0 interactive
echo   %0 analyze .\src\main report.html
echo   %0 strategic .\test-strategic-analysis.c
echo.
echo Prerequisites:
echo   - Docker Desktop must be installed and running
echo   - Copy .env.example to .env and configure API keys
echo   - Place source code in .\workspace directory for analysis
goto :eof

REM Main script logic
call :check_docker

set command=%1
if "%command%"=="" set command=help

if "%command%"=="build" goto :build
if "%command%"=="interactive" goto :interactive
if "%command%"=="i" goto :interactive
if "%command%"=="analyze" goto :analyze
if "%command%"=="a" goto :analyze
if "%command%"=="strategic" goto :strategic
if "%command%"=="s" goto :strategic
if "%command%"=="logs" goto :logs
if "%command%"=="l" goto :logs
if "%command%"=="stop" goto :stop
if "%command%"=="clean" goto :clean
if "%command%"=="help" goto :help
if "%command%"=="h" goto :help
if "%command%"=="--help" goto :help
if "%command%"=="-h" goto :help

call :log_error "Unknown command: %command%"
goto :help