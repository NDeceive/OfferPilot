param([switch]$SmokeTest)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$frontend = Join-Path $root '前端'
$backend = Join-Path $root '后端'
$logDir = Join-Path $root '后端\logs'
$frontendPort = if ($env:FRONTEND_PORT) { [int]$env:FRONTEND_PORT } else { 5173 }
$backendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$dbPort = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }
$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }
$url = "http://localhost:$frontendPort"
$backendUrl = "http://localhost:$backendPort"
if (-not $env:BACKEND_PROXY_TARGET) { $env:BACKEND_PROXY_TARGET = $backendUrl }
$digitalHumanEnabled = $env:DIGITAL_HUMAN_ENABLED -ne 'false'
$digitalHumanUrl = if ($env:DIGITAL_HUMAN_BASE_URL) { $env:DIGITAL_HUMAN_BASE_URL } else { 'http://127.0.0.1:8010' }
$digitalHumanPort = ([uri]$digitalHumanUrl).Port
$started = @()
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
$http = New-Object System.Net.Http.HttpClient($handler)
$http.Timeout = [TimeSpan]::FromSeconds(2)

function Test-WebReady([string]$Address, [switch]$Backend) {
    try {
        $response = $http.GetAsync($Address).GetAwaiter().GetResult()
        try {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ($Backend) {
                return ([int]$response.StatusCode -eq 401 -and $body -match '未登录或登录已过期')
            }
            return ($response.IsSuccessStatusCode -and $body -match '<title>OfferPilot')
        } finally { $response.Dispose() }
    } catch { return $false }
}

function Test-Port([int]$Port, [string]$Address = 'localhost') {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($Address, $Port)
        return ($task.Wait(1000) -and $client.Connected)
    } catch { return $false }
    finally { $client.Dispose() }
}

function Test-DigitalHumanReady {
    if (-not $digitalHumanEnabled) { return $true }
    try {
        $response = $http.GetAsync("$digitalHumanUrl/offerpilot-embed.html").GetAwaiter().GetResult()
        try {
            return $response.IsSuccessStatusCode -and ($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() -match 'OfferPilot')
        } finally { $response.Dispose() }
    } catch { return $false }
}

function Wait-Web([string]$Address, $Process, [switch]$Backend) {
    $deadline = (Get-Date).AddMinutes(15)
    while ((Get-Date) -lt $deadline) {
        if (Test-WebReady $Address -Backend:$Backend) { return }
        if ($Process.HasExited) { throw "服务启动失败，请查看日志：$logDir" }
        Start-Sleep -Seconds 2
    }
    throw "等待服务启动超时，请查看日志：$logDir"
}

function Initialize-ZhipuKey([bool]$BackendAlreadyRunning) {
    $configuredKey = [Environment]::GetEnvironmentVariable('ZHIPU_API_KEY', 'Process')
    if (-not [string]::IsNullOrWhiteSpace($configuredKey)) { return }
    $savedKey = [Environment]::GetEnvironmentVariable('ZHIPU_API_KEY', 'User')
    if (-not [string]::IsNullOrWhiteSpace($savedKey)) {
        [Environment]::SetEnvironmentVariable('ZHIPU_API_KEY', $savedKey, 'Process')
        return
    }
    if ($SmokeTest) { return }

    if ($BackendAlreadyRunning) {
        Write-Warning '后端已经运行，无法在当前启动窗口为它注入智谱 API Key。若语音识别提示未配置，请关闭后端，重新运行一键启动网页并按提示输入。'
        return
    }

    Write-Host '语音识别需要你自己的智谱 API Key；智能追问单独使用后端的 DeepSeek 配置。不配置智谱也可启动，但语音识别不可用。'
    $choice = Read-Host '现在配置智谱 API Key，并仅保存到当前 Windows 用户环境变量供下次启动使用？(Y/n)'
    if ($choice -match '^[nN]') {
        Write-Warning '已跳过智谱 API Key 配置；语音识别将不可用。配置方法见 README「配置智谱 API Key」。'
        return
    }

    $secureKey = Read-Host '请输入智谱 API Key（输入不会显示）' -AsSecureString
    $keyPointer = [IntPtr]::Zero
    try {
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
        $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
        if ([string]::IsNullOrWhiteSpace($plainKey)) {
            Write-Warning '未输入 API Key；语音识别将不可用。'
            return
        }
        [Environment]::SetEnvironmentVariable('ZHIPU_API_KEY', $plainKey, 'Process')
        try {
            [Environment]::SetEnvironmentVariable('ZHIPU_API_KEY', $plainKey, 'User')
            Write-Host '智谱 API Key 已保存到当前 Windows 用户环境变量，后端即将使用；不会写入项目文件。' -ForegroundColor Green
        } catch {
            Write-Warning '密钥仅对本次启动有效；保存到当前 Windows 用户环境变量失败，请按 README 手动配置。'
        }
    } finally {
        if ($keyPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
        }
        Remove-Variable plainKey, secureKey -ErrorAction SilentlyContinue
    }
}

try {
    Write-Host 'OfferPilot 一键启动' -ForegroundColor Cyan
    Write-Host '首次启动可能需要几分钟，请稍候。'
    $backendReady = Test-WebReady "$backendUrl/api/dashboard/overview" -Backend
    $frontendReady = Test-WebReady $url
    $digitalHumanReady = Test-DigitalHumanReady
    if (-not $backendReady -and (Test-Port $backendPort)) { throw "$backendPort 端口已被占用，但后端尚未就绪。请检查已有服务后重试。" }
    if (-not $frontendReady -and (Test-Port $frontendPort)) { throw "$frontendPort 端口已被占用，但不是就绪的 OfferPilot 网页。" }
    if (-not $digitalHumanReady -and (Test-Port $digitalHumanPort ([uri]$digitalHumanUrl).Host)) { throw '数字人服务地址已被其他程序占用。' }
    Initialize-ZhipuKey $backendReady
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null

    if (-not $frontendReady) {
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        $nodeVersion = [version]((& $node -p 'process.versions.node').Trim())
        if (-not (($nodeVersion.Major -eq 20 -and $nodeVersion -ge [version]'20.19') -or ($nodeVersion.Major -ge 22 -and $nodeVersion -ge [version]'22.12'))) {
            throw '请安装 Node.js 20.19+ 或 22.12+（Vite 8 要求）。'
        }
    }

    if (-not $backendReady) {
        if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) { throw '未找到 Java，请安装 JDK 17 或更高版本，并配置 PATH。' }
        if (-not $env:JAVA_HOME) {
            $javaPath = (Get-Command javac.exe -ErrorAction Stop).Source
            $env:JAVA_HOME = Split-Path (Split-Path $javaPath -Parent) -Parent
        }
        if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { throw 'JAVA_HOME 必须指向 JDK（含 javac），不能仅安装 JRE。' }
        $compilerVersion = & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -version 2>&1
        if ("$compilerVersion" -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 17) { throw '请安装 JDK 17 或更高版本，并修正 JAVA_HOME。' }
        if (-not (Test-Port $dbPort $dbHost)) { throw "无法连接 MySQL $dbHost`:$dbPort，请检查本地部署配置。" }
    }

    if (-not $digitalHumanReady) {
        if ($env:DIGITAL_HUMAN_LAUNCHER_ENABLED -eq 'false') { throw '数字人自动启动已关闭，指定的 DIGITAL_HUMAN_BASE_URL 尚不可用，请先启动外部服务。' }
        if (-not (Test-Port $dbPort $dbHost)) { throw "无法连接 MySQL $dbHost`:$dbPort，请检查本地部署配置。" }
        Write-Host '正在检查数字人 Python 环境、依赖和模型资源（首次可能需要较长时间）…'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root '数字人/start_offerpilot_digital_human.ps1') -CheckOnly
        if ($LASTEXITCODE -ne 0) { throw '数字人准备失败，请查看上方错误，修复后重新运行。' }
    }

    if (-not $backendReady) {
        Write-Host "正在启动后端（$backendPort）…"
        $backendProcess = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c .\mvnw.cmd clean spring-boot:run' -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web-backend.log') -RedirectStandardError (Join-Path $logDir 'web-backend-error.log')
        $started += $backendProcess
        Wait-Web "$backendUrl/api/dashboard/overview" $backendProcess -Backend
    }

    if (-not $digitalHumanReady) {
        if ($backendReady) {
            Write-Host '正在为已运行的后端启动数字人（8010）…'
            $digitalHumanProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $root '数字人/start_offerpilot_digital_human.ps1') + '"') -WorkingDirectory (Join-Path $root '数字人') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web-digital-human.log') -RedirectStandardError (Join-Path $logDir 'web-digital-human-error.log')
            $started += $digitalHumanProcess
        }
        $deadline = (Get-Date).AddMinutes(5)
        while (-not (Test-DigitalHumanReady)) {
            if ($backendProcess -and $backendProcess.HasExited) { throw "后端进程退出，请检查 $logDir/web-backend-error.log" }
            if ($digitalHumanProcess -and $digitalHumanProcess.HasExited) { throw "数字人进程退出，请检查 $logDir/web-digital-human-error.log" }
            if ((Get-Date) -gt $deadline) { throw "数字人未就绪，请检查 $logDir/digital-human.log" }
            Start-Sleep -Seconds 2
        }
    }

    if (-not $frontendReady) {
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        Push-Location $frontend
        try {
            & $env:ComSpec /d /c 'npm.cmd ls --depth=0 >nul 2>&1'
            if ($LASTEXITCODE -ne 0) {
                Write-Host '前端依赖缺失或版本不匹配，正在按锁文件重新安装…'
                & npm.cmd ci
                if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败，请检查网络和 Node.js 版本后重试。' }
            }
        } finally { Pop-Location }
        Write-Host "正在启动网页（$frontendPort）…"
        $frontendProcess = Start-Process -FilePath $node -ArgumentList "node_modules/vite/bin/vite.js --host localhost --port $frontendPort --strictPort" -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web-frontend.log') -RedirectStandardError (Join-Path $logDir 'web-frontend-error.log')
        $started += $frontendProcess
        Wait-Web $url $frontendProcess
    }

    if (-not (Test-WebReady "$url/api/dashboard/overview" -Backend)) { throw '网页已启动，但无法连接后端。请查看启动日志。' }
    Write-Host "网页与已启用的服务均已就绪：$url" -ForegroundColor Green
    if (-not $SmokeTest) {
        Start-Process $url
        if ($started.Count -gt 0) {
            Write-Host '使用期间请保留此窗口。'
            Read-Host '结束使用时按回车，停止本次启动的服务' | Out-Null
        }
    }
} catch {
    Write-Host "启动失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    foreach ($process in $started) {
        if (-not $process.HasExited) {
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        }
    }
    $http.Dispose()
    $handler.Dispose()
}
