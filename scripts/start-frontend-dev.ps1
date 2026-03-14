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
    -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'npm run dev -- --host 127.0.0.1 --port 4173' `
    -WorkingDirectory (Join-Path $root 'vue') `
    -PassThru `
    -RedirectStandardOutput $frontendLogOut `
    -RedirectStandardError $frontendLogErr

Start-Sleep -Seconds 6

try {
    $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:4173' -UseBasicParsing -TimeoutSec 5
    Write-Output "PID=$($proc.Id)"
    Write-Output "STATUS=$($resp.StatusCode)"
    Write-Output 'URL=http://127.0.0.1:4173'
}
catch {
    Write-Output "PID=$($proc.Id)"
    Write-Output 'STATUS=STARTED_BUT_NOT_READY'
    if (Test-Path $frontendLogErr) {
        Get-Content -Tail 40 $frontendLogErr
    }
}
