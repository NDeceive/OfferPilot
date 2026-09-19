@echo off

REM 切换到脚本所在目录
cd /d "%~dp0"

REM 启动 LiveTalking 服务
start "LiveTalking" ".\envs\nerfstream\python.exe" ".\app.py" --transport webrtc --model wav2lip

REM 延迟 5 秒让服务启动
ping 127.0.0.1 -n 6 > nul

REM 自动打开浏览器
start http://127.0.0.1:8010/dashboard.html

echo LiveTalking 服务已启动，浏览器已打开
echo 按任意键退出...
pause > nul
