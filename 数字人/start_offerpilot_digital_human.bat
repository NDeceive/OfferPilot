@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_offerpilot_digital_human.ps1" %*
exit /b %errorlevel%
