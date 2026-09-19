$ErrorActionPreference = "Stop"

$assetBaseUrl = "https://github.com/zyy060911/OfferPilot/releases/download/digital-human-assets-v1"
$digitalHumanRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$downloadDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "offerpilot-digital-human-assets"

New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null

function Install-AssetArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArchiveName
    )

    $archivePath = Join-Path $downloadDirectory $ArchiveName
    $downloadUrl = "$assetBaseUrl/$ArchiveName"

    Write-Host "[Digital Human] Downloading $ArchiveName ..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath

    Write-Host "[Digital Human] Extracting $ArchiveName ..."
    Expand-Archive -LiteralPath $archivePath -DestinationPath $digitalHumanRoot -Force
    Remove-Item -LiteralPath $archivePath -Force
}

if (-not (Test-Path -LiteralPath (Join-Path $digitalHumanRoot "models\wav2lip.pth"))) {
    Install-AssetArchive -ArchiveName "digital-human-models.zip"
}

if (-not (Test-Path -LiteralPath (Join-Path $digitalHumanRoot "data\avatars\wav2lip256_avatar1"))) {
    Install-AssetArchive -ArchiveName "digital-human-avatar.zip"
}

Write-Host "[Digital Human] Model and avatar assets are ready."
