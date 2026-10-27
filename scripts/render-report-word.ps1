param([Parameter(Mandatory=$true)][string]$Directory)
$ErrorActionPreference = 'Stop'
$reportRenderDirectory = (Resolve-Path -LiteralPath $Directory).Path
$reportWord = New-Object -ComObject Word.Application
$reportWord.Visible = $false
$reportWord.DisplayAlerts = 0
$reportWord.AutomationSecurity = 3
try {
    foreach ($reportName in @('standard', 'stress')) {
        $reportDocPath = Join-Path $reportRenderDirectory ($reportName + '.docx')
        $reportPdfPath = Join-Path $reportRenderDirectory ('word-' + $reportName + '.pdf')
        $reportDocument = $null
        try {
            $reportDocument = $reportWord.Documents.Open($reportDocPath, $false, $true, $false)
            $reportDocument.Fields.Update() | Out-Null
            $reportDocument.Repaginate()
            $reportDocument.ExportAsFixedFormat($reportPdfPath, 17)
            Write-Output ($reportName + ': ' + $reportDocument.ComputeStatistics(2) + ' pages')
        } finally {
            if ($null -ne $reportDocument) {
                $reportDocument.Close(0)
                [Runtime.InteropServices.Marshal]::FinalReleaseComObject($reportDocument) | Out-Null
            }
        }
    }
} finally {
    $reportWord.Quit(0)
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($reportWord) | Out-Null
}
