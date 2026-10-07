param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Test-Python310([string]$Executable, [string[]]$Prefix = @()) {
    try {
        & $Executable @Prefix -c 'import sys; sys.exit(0 if sys.version_info[:2] == (3, 10) else 1)' 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch { return $false }
}

try {
    Push-Location $root
    try {
        $python = $null
        if ($env:OFFERPILOT_PYTHON) {
            if (-not (Test-Python310 $env:OFFERPILOT_PYTHON)) { throw 'OFFERPILOT_PYTHON must point to a working Python 3.10 executable.' }
            $python = $env:OFFERPILOT_PYTHON
        } else {
            foreach ($candidate in @((Join-Path $root 'envs/nerfstream/python.exe'), (Join-Path $root '.venv/Scripts/python.exe'))) {
                if ((Test-Path $candidate) -and (Test-Python310 $candidate)) { $python = $candidate; break }
            }
        }
        if (-not $python) {
            $basePython = $null
            $prefix = @()
            if ((Get-Command py.exe -ErrorAction SilentlyContinue) -and (Test-Python310 'py.exe' @('-3.10'))) {
                $basePython = 'py.exe'; $prefix = @('-3.10')
            } elseif ((Get-Command python.exe -ErrorAction SilentlyContinue) -and (Test-Python310 'python.exe')) {
                $basePython = 'python.exe'
            }
            if (-not $basePython) { throw 'Install Python 3.10 with the Python launcher, or set OFFERPILOT_PYTHON to its executable.' }
            Write-Host '[Digital Human] Creating/repairing the local Python 3.10 environment...'
            & $basePython @prefix -m venv (Join-Path $root '.venv')
            if ($LASTEXITCODE -ne 0) { throw 'Python environment creation failed.' }
            $python = Join-Path $root '.venv/Scripts/python.exe'
        }
        Write-Host "[Digital Human] Python: $python"
        $importCheck = 'import app, avatars.wav2lip_avatar, tts.edge; from avatars.wav2lip.audio import melspectrogram; import numpy as np; melspectrogram(np.zeros(16000, dtype=np.float32))'
        $ErrorActionPreference = 'Continue'
        & $python -c $importCheck 1>$null 2>$null
        $dependencyExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($dependencyExit -ne 0) {
            Write-Host '[Digital Human] Installing/repairing Wav2Lip runtime dependencies...'
            & $python -m pip install --upgrade pip
            if ($LASTEXITCODE -ne 0) { throw 'pip setup failed; check network access to PyPI.' }
            & $python -m pip install -r (Join-Path $root 'requirements-offerpilot.txt')
            if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed; retry this launcher after restoring network access.' }
            & $python -c $importCheck
            if ($LASTEXITCODE -ne 0) { throw 'Digital human dependency check failed after installation.' }
        }
        & (Join-Path $root 'setup_offerpilot_assets.ps1')
        if ($CheckOnly) { Write-Host '[Digital Human] Environment and assets are ready.'; return }

        $listener = [Net.Sockets.TcpClient]::new()
        try {
            try { $listener.Connect('127.0.0.1', 8010) } catch {}
            if ($listener.Connected) { throw 'Port 8010 is already occupied. Reuse the existing service or stop it before starting another instance.' }
        } finally { $listener.Dispose() }
        $batch = 4
        if ($env:OFFERPILOT_BATCH_SIZE) {
            if (-not [int]::TryParse($env:OFFERPILOT_BATCH_SIZE, [ref]$batch) -or $batch -lt 1) { throw 'OFFERPILOT_BATCH_SIZE must be a positive integer.' }
        }
        & $python (Join-Path $root 'app.py') --transport webrtc --model wav2lip --batch_size $batch --listenport 8010
        if ($LASTEXITCODE -ne 0) { throw "Digital human exited with code $LASTEXITCODE." }
    } finally { Pop-Location }
} catch {
    Write-Host "[Digital Human] Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
