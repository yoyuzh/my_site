$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$backendLogOut = Join-Path $root 'backend-dev.out.log'
$backendLogErr = Join-Path $root 'backend-dev.err.log'
$frontendLogOut = Join-Path $root 'frontend-dev.out.log'
$frontendLogErr = Join-Path $root 'frontend-dev.err.log'
$javaExe = 'C:\Program Files\Java\jdk-22\bin\java.exe'

Remove-Item $backendLogOut, $backendLogErr, $frontendLogOut, $frontendLogErr -ErrorAction SilentlyContinue

$backend = Start-Process `
    -FilePath $javaExe `
    -ArgumentList '-jar', 'backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar', '--spring.profiles.active=dev' `
    -WorkingDirectory $root `
    -PassThru `
    -RedirectStandardOutput $backendLogOut `
    -RedirectStandardError $backendLogErr

try {
    $backendReady = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 2
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/swagger-ui.html' -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                $backendReady = $true
                break
            }
        }
        catch {
        }
    }

    if (-not $backendReady) {
        throw '后端启动失败'
    }

    $userSuffix = Get-Random -Minimum 1000 -Maximum 9999
    $username = "tester$userSuffix"
    $email = "tester$userSuffix@example.com"
    $password = 'pass123456'
    $registerBody = @{
        username = $username
        email = $email
        password = $password
    } | ConvertTo-Json

    $register = Invoke-RestMethod `
        -Uri 'http://127.0.0.1:8080/api/auth/register' `
        -Method Post `
        -ContentType 'application/json' `
        -Body $registerBody

    $token = $register.data.token
    if (-not $token) {
        throw '注册未返回 token'
    }

    $headers = @{ Authorization = "Bearer $token" }
    $profile = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/user/profile' -Headers $headers -Method Get
    if ($profile.data.username -ne $username) {
        throw '用户信息校验失败'
    }

    Invoke-RestMethod `
        -Uri 'http://127.0.0.1:8080/api/files/mkdir' `
        -Headers $headers `
        -Method Post `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body 'path=/docs' | Out-Null

    $tempFile = Join-Path $root 'backend-upload-smoke.txt'
    Set-Content -Path $tempFile -Value 'hello portal' -Encoding UTF8
    & curl.exe -s -X POST -H "Authorization: Bearer $token" -F "path=/docs" -F "file=@$tempFile" http://127.0.0.1:8080/api/files/upload | Out-Null

    $files = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/files/list?path=%2Fdocs&page=0&size=10' -Headers $headers -Method Get
    if ($files.data.items.Count -lt 1) {
        throw '文件列表为空'
    }

    $schedule = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/cqu/schedule?semester=2025-2026-1&studentId=20230001' -Headers $headers -Method Get
    if ($schedule.data.Count -lt 1) {
        throw '课表接口为空'
    }

    $frontend = Start-Process `
        -FilePath 'cmd.exe' `
        -ArgumentList '/c', 'npm run dev -- --host 127.0.0.1 --port 4173' `
        -WorkingDirectory (Join-Path $root 'vue') `
        -PassThru `
        -RedirectStandardOutput $frontendLogOut `
        -RedirectStandardError $frontendLogErr

    try {
        $frontendReady = $false
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 2
            try {
                $index = Invoke-WebRequest -Uri 'http://127.0.0.1:4173' -UseBasicParsing -TimeoutSec 3
                if ($index.StatusCode -eq 200) {
                    $frontendReady = $true
                    break
                }
            }
            catch {
            }
        }

        if (-not $frontendReady) {
            throw '前端启动失败'
        }

        Write-Output "BACKEND_OK username=$username"
        Write-Output "FILES_OK count=$($files.data.items.Count)"
        Write-Output "SCHEDULE_OK count=$($schedule.data.Count)"
        Write-Output 'FRONTEND_OK url=http://127.0.0.1:4173'
    }
    finally {
        if ($frontend -and -not $frontend.HasExited) {
            Stop-Process -Id $frontend.Id -Force
        }
    }
}
finally {
    Remove-Item (Join-Path $root 'backend-upload-smoke.txt') -ErrorAction SilentlyContinue
    if ($backend -and -not $backend.HasExited) {
        Stop-Process -Id $backend.Id -Force
    }
}
