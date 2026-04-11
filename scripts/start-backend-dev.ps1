$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$mavenExe = 'mvn.cmd'
$logsDir = Join-Path $root 'logs'
if (-not (Test-Path $logsDir)) {
    New-Item -Path $logsDir -ItemType Directory | Out-Null
}
$out = Join-Path $logsDir 'backend-dev.out.log'
$err = Join-Path $logsDir 'backend-dev.err.log'
$devJwtSecret = $env:APP_JWT_SECRET
$devDatasourceUrl = $env:SPRING_DATASOURCE_URL

function Get-DotEnvValue {
    param(
        [string]$Path,
        [string]$Name
    )

    if (-not (Test-Path $Path)) {
        return $null
    }

    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf('=')
        if ($separatorIndex -le 0) {
            continue
        }

        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        if ($key -ne $Name) {
            continue
        }

        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        if ($value.Length -ge 2) {
            $startsWithDoubleQuote = $value.StartsWith('"') -and $value.EndsWith('"')
            $startsWithSingleQuote = $value.StartsWith("'") -and $value.EndsWith("'")
            if ($startsWithDoubleQuote -or $startsWithSingleQuote) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        return $value
    }

    return $null
}

$dotEnvPath = Join-Path $root '.env'

if ([string]::IsNullOrWhiteSpace($devJwtSecret)) {
    $devJwtSecret = Get-DotEnvValue -Path $dotEnvPath -Name 'APP_JWT_SECRET'
}

if ([string]::IsNullOrWhiteSpace($devJwtSecret)) {
    $devJwtSecret = 'local-dev-jwt-secret-2026-04-09-yoyuzh-portal-very-long'
}

if ([string]::IsNullOrWhiteSpace($devDatasourceUrl)) {
    $devDatasourceUrl = Get-DotEnvValue -Path $dotEnvPath -Name 'SPRING_DATASOURCE_URL'
}

if ([string]::IsNullOrWhiteSpace($devDatasourceUrl)) {
    $devDatasourceUrl = 'jdbc:h2:file:./data/yoyuzh_portal_dev;MODE=MySQL;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1'
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
