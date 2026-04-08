$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$mavenExe = 'mvn.cmd'
$out = Join-Path $root 'backend-dev.out.log'
$err = Join-Path $root 'backend-dev.err.log'
$devJwtSecret = $env:APP_JWT_SECRET
$devDatasourceUrl = $env:SPRING_DATASOURCE_URL

if ([string]::IsNullOrWhiteSpace($devJwtSecret)) {
    $devJwtSecret = 'local-dev-jwt-secret-2026-04-09-yoyuzh-portal-very-long'
}

if ([string]::IsNullOrWhiteSpace($devDatasourceUrl)) {
    $devDatasourceUrl = 'jdbc:h2:mem:yoyuzh_portal_dev;MODE=MySQL;DB_CLOSE_DELAY=-1'
}

if (Test-Path $out) {
    Remove-Item $out -Force
}
if (Test-Path $err) {
    Remove-Item $err -Force
}

$proc = Start-Process `
    -FilePath 'cmd.exe' `
    -ArgumentList '/c', "set `"APP_JWT_SECRET=$devJwtSecret`" && set `"SPRING_DATASOURCE_URL=$devDatasourceUrl`" && $mavenExe spring-boot:run -Dspring-boot.run.profiles=dev" `
    -WorkingDirectory (Join-Path $root 'backend') `
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
