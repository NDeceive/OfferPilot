$root = Get-ChildItem -Path 'D:\wav2lip' -Directory |
    Where-Object { $_.Name -like 'wav2lip256*V1.1' } |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $root) {
    Write-Error 'VoxCPM root directory was not found under D:\wav2lip.'
    exit 1
}

$pythonExe = Join-Path $root 'wav2lip_voxcpm\python.exe'
$compatDir = Join-Path $PSScriptRoot 'voxcpm_compat'
$logDir = Join-Path $PSScriptRoot 'logs'
$stdoutLog = Join-Path $logDir 'voxcpm_tts.stdout.log'
$stderrLog = Join-Path $logDir 'voxcpm_tts.stderr.log'

if (-not (Test-Path $pythonExe)) {
    Write-Error "VoxCPM Python not found: $pythonExe"
    exit 1
}

if (-not (Test-Path $compatDir)) {
    Write-Error "VoxCPM compatibility directory not found: $compatDir"
    exit 1
}

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

foreach ($logFile in @($stdoutLog, $stderrLog)) {
    if (Test-Path $logFile) {
        Clear-Content -Path $logFile
    } else {
        New-Item -ItemType File -Path $logFile | Out-Null
    }
}

foreach ($line in netstat -ano | Select-String ':7860' | Select-String 'LISTENING') {
    $parts = ($line -split '\s+') | Where-Object { $_ }
    if ($parts.Length -ge 5) {
        $targetPid = $parts[-1]
        Write-Output "Closing existing process on port 7860: $targetPid"
        taskkill /PID $targetPid /F > $null 2> $null
    }
}

Start-Sleep -Seconds 2

$bootstrap = @"
`$env:PYTHONPATH = '$compatDir'
& '$pythonExe' 'webui.py' 1>> '$stdoutLog' 2>> '$stderrLog'
"@

$encodedBootstrap = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($bootstrap))

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'powershell.exe'
$psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encodedBootstrap"
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
[System.Diagnostics.Process]::Start($psi) | Out-Null

$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $response = Invoke-WebRequest `
            -UseBasicParsing `
            -Method Post `
            -ContentType 'application/json' `
            -Body '{"data":[]}' `
            'http://127.0.0.1:7860/gradio_api/run/lambda'
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
    }

    Start-Sleep -Milliseconds 500
}

if (-not $ready) {
    Write-Error 'VoxCPM TTS service did not become ready on port 7860.'
    exit 1
}
