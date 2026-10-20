param([string]$ServerAddress = '127.0.0.1')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if ($ServerAddress -notmatch '^[a-zA-Z0-9.-]+$') { throw 'Use a hostname or IPv4 address.' }
$java = (Get-ChildItem "$root/tools/jdk-*/bin/javaw.exe" | Select-Object -First 1).FullName
$instance = Get-Date -Format 'yyyyMMdd-HHmmssfff'
$profile = "$root/runtime/client-$instance"
New-Item -ItemType Directory -Path $profile -Force | Out-Null
Start-Process $java -WorkingDirectory $profile -ArgumentList @(
    "-Darmy.host=$ServerAddress", '-Darmy.port=8122', "-Duser.home=$profile",
    '-cp', "$root/desktop-build/build/install/army2-lan-desktop/lib/*", 'com.mygdx.game.DesktopLauncher'
) -WindowStyle Hidden -RedirectStandardOutput "$root/logs/client-$instance.out.log" -RedirectStandardError "$root/logs/client-$instance.err.log"
