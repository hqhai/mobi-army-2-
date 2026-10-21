$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
if (-not ([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Right-click Enable-LAN.cmd and choose Run as administrator.'
}
$java = (Get-ChildItem "$root/tools/jdk-*/bin/java.exe" | Select-Object -First 1).FullName
if (-not (Get-NetFirewallRule -Name 'MobiArmy2-LAN-8122' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'MobiArmy2-LAN-8122' -DisplayName 'MobiArmy2 LAN TCP 8122' `
        -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8122 `
        -RemoteAddress LocalSubnet -Profile Any -Program $java | Out-Null
}
if (-not (Get-NetFirewallRule -Name 'MobiArmy2-LAN-8123' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'MobiArmy2-LAN-8123' -DisplayName 'MobiArmy2 LAN registration page TCP 8123' `
        -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8123 `
        -RemoteAddress LocalSubnet -Profile Any -Program $java | Out-Null
}
Write-Host 'Allowed TCP 8122 (game) and 8123 (registration page) from the local subnet for this game server only.'
