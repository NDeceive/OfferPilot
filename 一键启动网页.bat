@echo off
setlocal
title OfferPilot
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-web.ps1"
if errorlevel 1 pause
