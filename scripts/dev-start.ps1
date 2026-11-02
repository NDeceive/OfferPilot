param([switch]$SmokeTest, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$voiceProcess = $null
$voiceEnabled = $env:INTERVIEW_VOICE_ENABLED -ne 'false'
$voiceUrl = if ($env:INTERVIEW_VOICE_URL) { $env:INTERVIEW_VOICE_URL } else { 'http://127.0.0.1:8020' }
$logDir = Join-Path $root '后端/logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
# Existing local credentials remain outside source control.
if (-not $env:SPRING_CONFIG_IMPORT) {
    $env:SPRING_CONFIG_IMPORT = 'optional:file:./config/ai.local.properties'
}
Add-Type -AssemblyName System.Net.Http
$voiceHandler = New-Object System.Net.Http.HttpClientHandler
$voiceHandler.UseProxy = $false
$voiceHttp = New-Object System.Net.Http.HttpClient($voiceHandler)
$voiceHttp.Timeout = [TimeSpan]::FromSeconds(2)
function Test-VoiceReady {
    try {
        $response = $voiceHttp.GetAsync("$voiceUrl/health").GetAwaiter().GetResult()
        try {
            $health = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            return $response.IsSuccessStatusCode -and $health.ok -eq $true
        } finally { $response.Dispose() }
    } catch { return $false }
}
try {
    $voiceReady = $false
    if ($voiceEnabled) {
        $voiceReady = Test-VoiceReady
        if (-not $voiceReady -and ([uri]$voiceUrl).Host -in @('localhost', '127.0.0.1')) {
            $voicePython = Join-Path $root '数字人/envs/speech/Scripts/python.exe'
            if (-not (Test-Path $voicePython)) { $voicePython = (Get-Command python.exe -ErrorAction Stop).Source }
            & $voicePython -X utf8 -c 'import numpy, parselmouth'
            if ($LASTEXITCODE -ne 0) { throw 'Voice analysis requires numpy and praat-parselmouth in the Python environment.' }
            $env:VOICE_PORT = ([uri]$voiceUrl).Port
            $voiceProcess = Start-Process $voicePython -ArgumentList '-X utf8 app.py' -WorkingDirectory (Join-Path $root '语音分析') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'voice-analysis.log') -RedirectStandardError (Join-Path $logDir 'voice-analysis-error.log')
            $voiceDeadline = (Get-Date).AddSeconds(20)
            while (-not (Test-VoiceReady)) {
                if ($voiceProcess.HasExited) { throw "Voice analysis exited; see $logDir/voice-analysis-error.log" }
                if ((Get-Date) -ge $voiceDeadline) { throw 'Voice analysis did not become ready within 20 seconds.' }
                Start-Sleep -Milliseconds 300
            }
        } elseif (-not $voiceReady) {
            throw 'The configured external voice analysis service is unavailable.'
        }
    }
    & (Join-Path $PSScriptRoot 'start-web.ps1') -SmokeTest:$SmokeTest -NoBrowser:$NoBrowser -ExternalServiceStarted:($null -ne $voiceProcess)
} finally {
    if ($voiceProcess -and -not $voiceProcess.HasExited) { Stop-Process -Id $voiceProcess.Id }
    $voiceHttp.Dispose()
    $voiceHandler.Dispose()
}
