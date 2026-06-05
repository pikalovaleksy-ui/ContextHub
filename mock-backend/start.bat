@echo off
chcp 65001 >nul
title Mock Backend - ContextHub

:: Проверяем, установлены ли пакеты
py -c "import fastapi" 2>nul
if errorlevel 1 (
    echo First run detected. Installing dependencies...
    py -m pip install fastapi uvicorn pydantic paho-mqtt --quiet
)

echo Starting services...
start "API Server" cmd /k "cd /d "%~dp0api_server" && py main.py"

echo.
echo ========================================
echo   Mock Server is running!
echo   Web UI Simulator: http://localhost:8080/simulator
echo ========================================
pause