$ErrorActionPreference = "SilentlyContinue"
$root = "C:\Users\LENOVO\Desktop\self\competition\bigbed\offerpilot"

$secret = "C:\Users\LENOVO\.offerpilot\voice.env"
if (Test-Path $secret) {
  $raw = Get-Content $secret -Raw
  $m = [regex]::Match($raw, "ZHIPU_API_KEY=(\S+)")
  if ($m.Success) { $env:ZHIPU_API_KEY = $m.Groups[1].Value }
  $d = [regex]::Match($raw, "DEEPSEEK_API_KEY=(\S+)")
  if ($d.Success) { $env:DEEPSEEK_API_KEY = $d.Groups[1].Value }
}

function Code($url) { curl.exe -s -o NUL -w "%{http_code}" --max-time 2 $url }

$b = Code "http://localhost:8080/"
$f = Code "http://localhost:5173/"

if ($b -eq "000" -or $b -eq "") {
  $mem = "C:\Users\LENOVO\.claude\projects\C--Users-LENOVO-Desktop-self-competition-bigbed\memory\dev-environment.md"
  $env:DB_PASSWORD = [regex]::Match((Get-Content $mem -Raw), "DB_PASSWORD=(\S+)").Groups[1].Value
  $env:JAVA_HOME = "C:\Users\LENOVO\apk-builder\jdk21"
  $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
  Start-Process cmd.exe -ArgumentList "/c","mvnw spring-boot:run > dev-start-backend.log 2>&1" -WorkingDirectory "$root\后端" -WindowStyle Hidden
  $b = "000"
}
if ($f -eq "000" -or $f -eq "") {
  Start-Process cmd.exe -ArgumentList "/c","npm run dev > dev-start-frontend.log 2>&1" -WorkingDirectory "$root\前端" -WindowStyle Hidden
  $f = "000"
}

for ($i = 0; $i -lt 60 -and ($b -eq "000" -or $f -eq "000"); $i++) {
  if ($b -eq "000") { $b = Code "http://localhost:8080/" }
  if ($f -eq "000") { $f = Code "http://localhost:5173/" }
  if ($b -eq "000" -or $f -eq "000") { Start-Sleep -Milliseconds 800 }
}

$opened = "no"
if ($f -ne "000") {
  Start-Process "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" -ArgumentList "--new-window","http://localhost:5173/"
  $opened = "opened (edge)"
}

$bpid = (Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1).OwningProcess

Write-Output "[dev-start]"
Write-Output ("PORT 8080: " + $(if ($b -ne "000") { "up (pid $bpid)" } else { "down" }) + " | PORT 5173: " + $(if ($f -ne "000") { "up" } else { "down" }))
Write-Output ("HTTP: 8080=$b 5173=$f")
Write-Output "URL: http://localhost:5173/"
Write-Output "ACCOUNT: student / 123456"
Write-Output ("BROWSER: " + $opened)
