$ErrorActionPreference='Stop'
Add-Type -AssemblyName Microsoft.VisualBasic
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$finalRoot=Join-Path $workspaceRoot 'designs/animation-navigation-correction'
$plan=Get-Content -LiteralPath (Join-Path $finalRoot 'reports/清理清单.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$retained=[IO.Path]::GetFullPath($plan.preserved_deck)
$targets=@($plan.delete_directories)+@($plan.delete_files)
foreach($target in $targets){
 $absolute=[IO.Path]::GetFullPath($target)
 if(-not $absolute.StartsWith($workspaceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Target outside workspace'}
 if($absolute -eq $retained -or $retained.StartsWith($absolute+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Target contains retained presentation'}
}
$count=0;$bytes=0;$failed=@()
foreach($target in $targets){
 if(-not (Test-Path -LiteralPath $target)){continue}
 try{
  $item=Get-Item -LiteralPath $target
  if($item.PSIsContainer){
   $size=(Get-ChildItem -LiteralPath $target -Recurse -File | Measure-Object Length -Sum).Sum
   [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($target,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
  }else{
   $size=$item.Length
   [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($target,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
  }
  $count++;$bytes+=$size
 }catch{$failed+=@{path=$target;reason=$_.Exception.Message}}
}
$hash=(Get-FileHash -LiteralPath $retained -Algorithm SHA256).Hash.ToLower()
if($hash -ne $plan.deck_sha256){throw 'Retained presentation changed'}
$result=@{recycledTargets=$count;removedFromWorkspaceMB=[Math]::Round($bytes/1MB,1);preserved=$retained;hashVerified=$true;failures=$failed}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $finalRoot 'reports/清理结果.json') -Encoding UTF8
$result | ConvertTo-Json -Depth 4
