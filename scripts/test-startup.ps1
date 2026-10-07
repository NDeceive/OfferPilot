param([string]$AssetCache = (Join-Path ([IO.Path]::GetTempPath()) 'offerpilot-digital-human-assets'))
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
foreach ($file in @('scripts/start-web.ps1', '数字人/start_offerpilot_digital_human.ps1', '数字人/setup_offerpilot_assets.ps1')) {
    $tokens = $null; $errors = $null
    [Management.Automation.Language.Parser]::ParseFile((Join-Path $root $file), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count) { throw ($errors.Message -join '; ') }
}
foreach ($name in @('digital-human-models.zip', 'digital-human-avatar.zip', 'digital-human-interviewer-v2.zip')) {
    if (-not (Test-Path (Join-Path $AssetCache $name))) { throw "Missing cached test archive: $name. Run digital human setup first, or pass -AssetCache." }
}
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path $root '.codex-tmp'))
$fixture = Join-Path $fixtureRoot ('startup-assets-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
try {
    $setup = Join-Path $fixture 'setup_offerpilot_assets.ps1'
    Copy-Item -LiteralPath (Join-Path $root '数字人/setup_offerpilot_assets.ps1') -Destination $setup
    & $setup -DownloadDirectory $AssetCache
    $missingFrame = Join-Path $fixture 'data/avatars/offerpilot_interviewer_v2/full_imgs/00000000.png'
    Remove-Item -LiteralPath $missingFrame
    [IO.File]::WriteAllBytes((Join-Path $fixture 'models/s3fd.pth'), [byte[]]@(0))
    & $setup -DownloadDirectory $AssetCache
    if (-not (Test-Path $missingFrame) -or (Get-Item (Join-Path $fixture 'models/s3fd.pth')).Length -ne 89843225) {
        throw 'Partial asset repair failed.'
    }
    $savedPython = $env:OFFERPILOT_PYTHON
    try {
        $env:OFFERPILOT_PYTHON = Join-Path $fixture 'missing-python.exe'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root '数字人/start_offerpilot_digital_human.ps1') -CheckOnly
        if ($LASTEXITCODE -eq 0) { throw 'Invalid Python executable was accepted.' }
    } finally { $env:OFFERPILOT_PYTHON = $savedPython }
    Write-Host 'Startup checks passed: PowerShell syntax, clean asset install, partial asset repair, invalid Python rejection.'
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    if ($resolvedFixture.StartsWith($fixtureRoot + [IO.Path]::DirectorySeparatorChar) -and (Split-Path $resolvedFixture -Leaf) -like 'startup-assets-*') {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
