param([string]$ServerAddress = '127.0.0.1')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if ($ServerAddress -notmatch '^[a-zA-Z0-9.-]+$') { throw 'Use a hostname or IPv4 address.' }
$lib = "$root/runtime/thor-projectiles-v2/army2-lan-desktop/lib"
if (!(Test-Path "$lib/army2-lan-desktop.jar")) { $lib = "$root/runtime/kingkong-v9/army2-lan-desktop/lib" }
if (!(Test-Path "$lib/army2-lan-desktop.jar")) {
    $lib = "$root/desktop-build/build/install/army2-lan-desktop/lib"
    if (!(Test-Path "$lib/army2-lan-desktop.jar")) { throw 'Run scripts/Build.ps1 first.' }
}
$instance = Get-Date -Format 'yyyyMMdd-HHmmssfff'
$profile = "$root/runtime/hero-effects-$instance"
New-Item -ItemType Directory -Force "$profile/tmp", "$root/logs" | Out-Null
Start-Process "$root/tools/jdk-21/bin/javaw.exe" -WorkingDirectory $profile -ArgumentList @(
    "-Darmy.host=$ServerAddress", '-Darmy.port=8122',
    "-Duser.home=$profile", "-Djava.io.tmpdir=$profile/tmp",
    '-cp', "$lib/*", 'com.mygdx.game.DesktopLauncher'
) -WindowStyle Hidden -RedirectStandardOutput "$root/logs/hero-effects-$instance.out.log" -RedirectStandardError "$root/logs/hero-effects-$instance.err.log"
