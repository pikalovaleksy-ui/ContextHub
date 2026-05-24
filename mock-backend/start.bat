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
timeout /t 2 /nobreak >nul
start "Radar Simulator" cmd /k "cd /d "%~dp0radar_simulator" && py simulator.py"

echo.
echo ========================================
echo   Both services are running!
echo ========================================
pause