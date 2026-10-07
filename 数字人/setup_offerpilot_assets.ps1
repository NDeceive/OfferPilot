param([string]$DownloadDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) 'offerpilot-digital-human-assets'))
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$assetBaseUrl = "https://github.com/zyy060911/OfferPilot/releases/download/digital-human-assets-v1"
$digitalHumanRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null

function Install-AssetArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArchiveName,
        [string]$DestinationPath = $digitalHumanRoot,
        [Parameter(Mandatory = $true)]
        [string]$Sha256
    )

    $archivePath = Join-Path $downloadDirectory $ArchiveName
    $downloadUrl = "$assetBaseUrl/$ArchiveName"

    if (-not ((Test-Path -LiteralPath $archivePath) -and (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -eq $Sha256)) {
        $partialPath = "$archivePath.partial"
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                Write-Host "[Digital Human] Downloading $ArchiveName (attempt $attempt/3) ..."
                Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $partialPath -TimeoutSec 1200
                if ((Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash -ne $Sha256) { throw "Checksum mismatch for $ArchiveName." }
                Move-Item -LiteralPath $partialPath -Destination $archivePath -Force
                break
            } catch {
                if (Test-Path -LiteralPath $partialPath) { Remove-Item -LiteralPath $partialPath -Force }
                if ($attempt -eq 3) { throw "Download failed: $downloadUrl. $($_.Exception.Message)" }
                Start-Sleep -Seconds 2
            }
        }
    }

    Write-Host "[Digital Human] Extracting $ArchiveName ..."
    Expand-Archive -LiteralPath $archivePath -DestinationPath $DestinationPath -Force
}

function Test-Model([string]$Name, [long]$Length) {
    $path = Join-Path $digitalHumanRoot "models/$Name"
    return (Test-Path -LiteralPath $path -PathType Leaf) -and (Get-Item -LiteralPath $path).Length -eq $Length
}

function Test-Avatar([string]$Name, [int]$FrameCount) {
    $path = Join-Path $digitalHumanRoot "data/avatars/$Name"
    if (-not (Test-Path -LiteralPath "$path/coords.pkl" -PathType Leaf) -or (Get-Item -LiteralPath "$path/coords.pkl").Length -eq 0) { return $false }
    foreach ($folder in @('full_imgs', 'face_imgs')) {
        $frames = @(Get-ChildItem -LiteralPath "$path/$folder" -Filter '*.png' -File -ErrorAction SilentlyContinue)
        if ($frames.Count -ne $FrameCount -or @($frames | Where-Object Length -eq 0).Count -gt 0) { return $false }
        for ($i = 0; $i -lt $FrameCount; $i++) {
            if (-not (Test-Path -LiteralPath (Join-Path "$path/$folder" ('{0:D8}.png' -f $i)))) { return $false }
        }
    }
    return $true
}

if (-not ((Test-Model 'wav2lip.pth' 214670409) -and (Test-Model 's3fd.pth' 89843225))) {
    Install-AssetArchive -ArchiveName 'digital-human-models.zip' -Sha256 '5DA77012FC73DA676C8BAE298BA5E3AA8139E35EF4FF757F3B4BDE6CD17B50D6'
}
if (-not (Test-Avatar 'wav2lip256_avatar1' 349)) {
    Install-AssetArchive -ArchiveName 'digital-human-avatar.zip' -Sha256 'C88EE9522F14EC5EB9EE1C4CA157E66185D42E6F9E42B37562ADB1D5C7878EF6'
}
if (-not (Test-Avatar 'offerpilot_interviewer_v2' 122)) {
    Install-AssetArchive -ArchiveName "digital-human-interviewer-v2.zip" `
        -DestinationPath (Join-Path $digitalHumanRoot "data\avatars") `
        -Sha256 "045DBB0488651BCCF42DD3D8022AAA7DD72847010AF028A0F3DC341E6862D90C"
}

if (-not ((Test-Model 'wav2lip.pth' 214670409) -and (Test-Model 's3fd.pth' 89843225) -and
          (Test-Avatar 'wav2lip256_avatar1' 349) -and (Test-Avatar 'offerpilot_interviewer_v2' 122))) {
    throw '[Digital Human] Extracted assets are incomplete. Check disk space and antivirus permissions.'
}

Write-Host "[Digital Human] Model and avatar assets are ready."
