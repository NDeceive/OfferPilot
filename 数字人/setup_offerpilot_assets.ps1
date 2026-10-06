$ErrorActionPreference = "Stop"

$assetBaseUrl = "https://github.com/zyy060911/OfferPilot/releases/download/digital-human-assets-v1"
$digitalHumanRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$downloadDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "offerpilot-digital-human-assets"

New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null

function Install-AssetArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArchiveName,
        [string]$DestinationPath = $digitalHumanRoot,
        [string]$Sha256 = ""
    )

    $archivePath = Join-Path $downloadDirectory $ArchiveName
    $downloadUrl = "$assetBaseUrl/$ArchiveName"

    Write-Host "[Digital Human] Downloading $ArchiveName ..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath

    if ($Sha256 -and (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $Sha256) {
        throw "[Digital Human] Checksum mismatch for $ArchiveName; the archive was not extracted."
    }

    Write-Host "[Digital Human] Extracting $ArchiveName ..."
    Expand-Archive -LiteralPath $archivePath -DestinationPath $DestinationPath -Force
    Remove-Item -LiteralPath $archivePath -Force
}

if (-not (Test-Path -LiteralPath (Join-Path $digitalHumanRoot "models\wav2lip.pth"))) {
    Install-AssetArchive -ArchiveName "digital-human-models.zip"
}

if (-not (Test-Path -LiteralPath (Join-Path $digitalHumanRoot "data\avatars\wav2lip256_avatar1"))) {
    Install-AssetArchive -ArchiveName "digital-human-avatar.zip"
}

$interviewerV2 = Join-Path $digitalHumanRoot "data\avatars\offerpilot_interviewer_v2"
if (-not ((Test-Path -LiteralPath (Join-Path $interviewerV2 "coords.pkl")) -and
           (Test-Path -LiteralPath (Join-Path $interviewerV2 "full_imgs")) -and
           (Test-Path -LiteralPath (Join-Path $interviewerV2 "face_imgs")))) {
    Install-AssetArchive -ArchiveName "digital-human-interviewer-v2.zip" `
        -DestinationPath (Join-Path $digitalHumanRoot "data\avatars") `
        -Sha256 "045DBB0488651BCCF42DD3D8022AAA7DD72847010AF028A0F3DC341E6862D90C"
}

Write-Host "[Digital Human] Model and avatar assets are ready."
