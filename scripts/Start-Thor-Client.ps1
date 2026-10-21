$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$profile = "$root/runtime/thor-player"
New-Item -ItemType Directory -Force "$profile/tmp", "$root/logs" | Out-Null
& "$root/scripts/Start-Server.ps1"
Start-Process "$root/tools/jdk-21/bin/javaw.exe" -WorkingDirectory $profile -ArgumentList @(
    '-Darmy.host=127.0.0.1', '-Darmy.port=8122',
    "-Duser.home=$profile", "-Djava.io.tmpdir=$profile/tmp",
    '-cp', "$root/desktop-build/build/install/army2-lan-desktop/lib/*",
    'com.mygdx.game.DesktopLauncher'
) -RedirectStandardOutput "$root/logs/thor-client.out.log" -RedirectStandardError "$root/logs/thor-client.err.log"
