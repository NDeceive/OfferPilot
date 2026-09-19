@echo off
cd /d "%~dp0"

if not exist ".\models\wav2lip.pth" (
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\setup_offerpilot_assets.ps1"
  if errorlevel 1 exit /b 1
)

if not exist ".\data\avatars\wav2lip256_avatar1" (
  powershell -NoProfile -ExecutionPolicy Bypass -File ".\setup_offerpilot_assets.ps1"
  if errorlevel 1 exit /b 1
)

set "PYTHON_EXE="
if exist ".\envs\nerfstream\python.exe" set "PYTHON_EXE=.\envs\nerfstream\python.exe"
if not defined PYTHON_EXE if exist ".\.venv\Scripts\python.exe" set "PYTHON_EXE=.\.venv\Scripts\python.exe"

if not defined PYTHON_EXE (
  echo [Digital Human] First run: creating Python 3.10 environment...
  where py >nul 2>nul
  if errorlevel 1 (
    echo [Digital Human] Python launcher not found. Install Python 3.10 first.
    exit /b 1
  )
  py -3.10 -m venv ".\.venv"
  if errorlevel 1 exit /b 1
  set "PYTHON_EXE=.\.venv\Scripts\python.exe"
  "%PYTHON_EXE%" -m pip install --upgrade pip
  if errorlevel 1 exit /b 1
  "%PYTHON_EXE%" -m pip install -r ".\requirements.txt"
  if errorlevel 1 exit /b 1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8010" ^| findstr "LISTENING"') do (
  echo Closing existing process on port 8010: %%a
  taskkill /PID %%a /F >nul 2>nul
)

timeout /t 2 /nobreak >nul

"%PYTHON_EXE%" ".\app.py" --transport webrtc --model wav2lip --listenport 8010
