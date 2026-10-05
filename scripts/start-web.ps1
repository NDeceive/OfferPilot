param([switch]$SmokeTest)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$frontend = Join-Path $root '前端'
$backend = Join-Path $root '后端'
$logDir = Join-Path $root '后端\logs'
$url = 'http://localhost:5173'
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

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync('localhost', $Port)
        return ($task.Wait(1000) -and $client.Connected)
    } catch { return $false }
    finally { $client.Dispose() }
}

function Wait-Web([string]$Address, $Process, [switch]$Backend) {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        if (Test-WebReady $Address -Backend:$Backend) { return }
        if ($Process.HasExited) { throw "服务启动失败，请查看日志：$logDir" }
        Start-Sleep -Seconds 2
    }
    throw "等待服务启动超时，请查看日志：$logDir"
}

try {
    Write-Host 'OfferPilot 一键启动' -ForegroundColor Cyan
    Write-Host '首次启动可能需要几分钟，请稍候。'
    $backendReady = Test-WebReady 'http://localhost:8080/api/dashboard/overview' -Backend
    $frontendReady = Test-WebReady $url
    if (-not $backendReady -and (Test-Port 8080)) { throw '8080 端口已被占用，但后端尚未就绪。请检查已有服务后重试。' }
    if (-not $frontendReady -and (Test-Port 5173)) { throw '5173 端口已被占用，但不是就绪的 OfferPilot 网页。请检查已有服务后重试。' }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null

    if (-not $backendReady) {
        if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) { throw '未找到 Java，请安装 JDK 17 或更高版本，并配置 PATH。' }
        if (-not $env:JAVA_HOME) {
            $javaPath = (Get-Command javac.exe -ErrorAction Stop).Source
            $env:JAVA_HOME = Split-Path (Split-Path $javaPath -Parent) -Parent
        }
        if (-not (Test-Port 3306)) { throw 'MySQL 未在 localhost:3306 运行。请先启动 MySQL，并按 README 初始化数据库。' }
        Write-Host '正在启动后端（8080）…'
        $backendProcess = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c .\mvnw.cmd clean spring-boot:run' -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web-backend.log') -RedirectStandardError (Join-Path $logDir 'web-backend-error.log')
        $started += $backendProcess
        Wait-Web 'http://localhost:8080/api/dashboard/overview' $backendProcess -Backend
    }

    if (-not $frontendReady) {
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        if (-not (Test-Path (Join-Path $frontend 'node_modules\vite\bin\vite.js'))) {
            Write-Host '正在安装前端依赖…'
            Push-Location $frontend
            try {
                & npm.cmd ci
                if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败，请检查网络和 Node.js 版本后重试。' }
            } finally { Pop-Location }
        }
        Write-Host '正在启动网页（5173）…'
        $frontendProcess = Start-Process -FilePath $node -ArgumentList 'node_modules/vite/bin/vite.js --host localhost --port 5173 --strictPort' -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web-frontend.log') -RedirectStandardError (Join-Path $logDir 'web-frontend-error.log')
        $started += $frontendProcess
        Wait-Web $url $frontendProcess
    }

    if (-not (Test-WebReady "$url/api/dashboard/overview" -Backend)) { throw '网页已启动，但无法连接后端。请查看启动日志。' }
    Write-Host "网页已就绪：$url" -ForegroundColor Green
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
