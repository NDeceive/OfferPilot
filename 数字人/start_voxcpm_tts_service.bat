@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\start_voxcpm_tts_service.ps1"
exit /b %ERRORLEVEL%
