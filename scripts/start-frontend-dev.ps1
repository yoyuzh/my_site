$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$frontendLogOut = Join-Path $root 'frontend-dev.out.log'
$frontendLogErr = Join-Path $root 'frontend-dev.err.log'

if (Test-Path $frontendLogOut) {
    Remove-Item $frontendLogOut -Force
}
if (Test-Path $frontendLogErr) {
    Remove-Item $frontendLogErr -Force
}

$proc = Start-Process `
    -FilePath 'npm.cmd' `
    -ArgumentList 'run', 'dev' `
    -WorkingDirectory (Join-Path $root 'front') `
    -PassThru `
    -RedirectStandardOutput $frontendLogOut `
    -RedirectStandardError $frontendLogErr

Start-Sleep -Seconds 6

try {
    $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:3000' -UseBasicParsing -TimeoutSec 5
    Write-Output "PID=$($proc.Id)"
    Write-Output "STATUS=$($resp.StatusCode)"
    Write-Output 'URL=http://127.0.0.1:3000'
}
catch {
    Write-Output "PID=$($proc.Id)"
    Write-Output 'STATUS=STARTED_BUT_NOT_READY'
    if (Test-Path $frontendLogErr) {
        Get-Content -Tail 40 $frontendLogErr
    }
}
