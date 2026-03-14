$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$javaExe = 'C:\Program Files\Java\jdk-22\bin\java.exe'
$out = Join-Path $root 'backend-dev.out.log'
$err = Join-Path $root 'backend-dev.err.log'

if (Test-Path $out) {
    Remove-Item $out -Force
}
if (Test-Path $err) {
    Remove-Item $err -Force
}

$proc = Start-Process `
    -FilePath $javaExe `
    -ArgumentList '-jar', 'backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar', '--spring.profiles.active=dev' `
    -WorkingDirectory $root `
    -PassThru `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err

Start-Sleep -Seconds 10

try {
    $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/swagger-ui.html' -UseBasicParsing -TimeoutSec 5
    Write-Output "PID=$($proc.Id)"
    Write-Output "STATUS=$($resp.StatusCode)"
    Write-Output 'URL=http://127.0.0.1:8080/swagger-ui.html'
}
catch {
    Write-Output "PID=$($proc.Id)"
    Write-Output 'STATUS=STARTED_BUT_NOT_READY'
    if (Test-Path $err) {
        Get-Content -Tail 40 $err
    }
}
