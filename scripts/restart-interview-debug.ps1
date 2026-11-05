$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$logDirectory = Join-Path $projectRoot '后端/logs'
$backendListener = Get-NetTCPConnection -State Listen -LocalPort 8080 | Select-Object -First 1
$avatarListener = Get-NetTCPConnection -State Listen -LocalPort 8010 | Select-Object -First 1
$backendProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($backendListener.OwningProcess)"
$avatarProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($avatarListener.OwningProcess)"
if ($backendProcess.Name -ne 'java.exe' -or -not $backendProcess.CommandLine.Contains($projectRoot) -or -not $backendProcess.CommandLine.Contains('com.zhimian.ZhimianApplication')) { throw '8080 is not this project backend.' }
if ($avatarProcess.Name -ne 'python.exe' -or -not $avatarProcess.CommandLine.Contains((Join-Path $projectRoot '数字人/app.py'))) { throw '8010 is not this project digital human.' }
if ($backendProcess.CommandLine -notmatch '^"[^"]+"\s+(.+)$') { throw 'Cannot preserve backend Java arguments.' }
$javaArguments = $Matches[1]
foreach ($setting in @('DEEPSEEK_API_KEY')) {
    if (-not [Environment]::GetEnvironmentVariable($setting, 'Process')) {
        [Environment]::SetEnvironmentVariable($setting, [Environment]::GetEnvironmentVariable($setting, 'User'), 'Process')
    }
}
$env:SPRING_CONFIG_IMPORT = 'optional:file:./config/ai.local.properties'
$env:DIGITAL_HUMAN_LAUNCHER_ENABLED = 'false'
Stop-Process -Id $backendProcess.ProcessId -Force
Stop-Process -Id $avatarProcess.ProcessId -Force
Start-Sleep -Milliseconds 800
$newAvatar = Start-Process powershell.exe -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $projectRoot '数字人/start_offerpilot_digital_human.ps1') + '"') -WorkingDirectory (Join-Path $projectRoot '数字人') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'debug-digital-human.log') -RedirectStandardError (Join-Path $logDirectory 'debug-digital-human-error.log')
$newBackend = Start-Process $backendProcess.ExecutablePath -ArgumentList $javaArguments -WorkingDirectory (Join-Path $projectRoot '后端') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'debug-backend.log') -RedirectStandardError (Join-Path $logDirectory 'debug-backend-error.log')
[pscustomobject]@{BackendProcess=$newBackend.Id; DigitalHumanLauncher=$newAvatar.Id; RestartedAt=(Get-Date).ToString('o')} | ConvertTo-Json
