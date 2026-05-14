param(
    [string]$DavHost = "api.yoyuzh.xyz",
    [string]$DavUrl = "https://api.yoyuzh.xyz/api/dav/"
)

$ErrorActionPreference = "Stop"

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "请用管理员身份运行 PowerShell，然后重新执行这个脚本。"
    }
}

function Set-DwordValue {
    param(
        [string]$Path,
        [string]$Name,
        [int]$Value
    )
    if (-not (Test-Path $Path)) {
        New-Item -Path $Path -Force | Out-Null
    }
    New-ItemProperty -Path $Path -Name $Name -Value $Value -PropertyType DWord -Force | Out-Null
}

function Set-MultiStringValue {
    param(
        [string]$Path,
        [string]$Name,
        [string[]]$Value
    )
    if (-not (Test-Path $Path)) {
        New-Item -Path $Path -Force | Out-Null
    }
    New-ItemProperty -Path $Path -Name $Name -Value $Value -PropertyType MultiString -Force | Out-Null
}

Assert-Administrator

$webClientParameters = "HKLM:\SYSTEM\CurrentControlSet\Services\WebClient\Parameters"
$authForwardTargets = @(
    "https://$DavHost",
    "https://$DavHost/",
    "https://$DavHost/api",
    "https://$DavHost/api/",
    "https://$DavHost/api/dav",
    "https://$DavHost/api/dav/"
)

Write-Host "配置 Windows WebClient Basic Auth 转发白名单..."
Set-DwordValue -Path $webClientParameters -Name "BasicAuthLevel" -Value 1
Set-MultiStringValue -Path $webClientParameters -Name "AuthForwardServerList" -Value $authForwardTargets

$officeVersions = @("16.0", "15.0", "14.0")
foreach ($version in $officeVersions) {
    $officeInternet = "HKCU:\Software\Microsoft\Office\$version\Common\Internet"
    Write-Host "配置 Office $version 使用 WebDAV 打开 Office 文档..."
    Set-DwordValue -Path $officeInternet -Name "FSSHTTPOff" -Value 1
}

Write-Host "重启 WebClient 服务..."
Set-Service -Name WebClient -StartupType Automatic
Restart-Service -Name WebClient -Force

Write-Host ""
Write-Host "配置完成。建议先断开旧映射盘，然后重新映射："
Write-Host "  $DavUrl"
Write-Host ""
Write-Host "如果 Word/Excel 仍然用旧凭据，请在控制面板的“凭据管理器”里删除 api.yoyuzh.xyz 相关 Windows 凭据后再连接。"
