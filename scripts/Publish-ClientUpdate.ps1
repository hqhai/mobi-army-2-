# Publishes the current client build so players' launchers update themselves on next start.
# Run after building the client (desktop-build installDist).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$lib = "$root/desktop-build/build/install/army2-lan-desktop/lib"
$pkg = "$root/runtime/pkg/MobiArmy2-PC"
$update = "$root/web/downloads/update"
$version = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force $update | Out-Null
Compress-Archive "$lib/*" "$update/lib.zip" -Force
# Keep the full download package in step with the update.
if (Test-Path "$pkg/lib") { Remove-Item "$pkg/lib" -Recurse -Force }
Copy-Item $lib "$pkg/lib" -Recurse
Set-Content "$pkg/version.txt" $version -Encoding ascii
Compress-Archive $pkg "$root/web/downloads/MobiArmy2-PC.zip" -Force
& "C:/Program Files/Git/bin/bash.exe" "$root/scripts/Publish-MacPackage.sh"
if ($LASTEXITCODE -ne 0) { throw "Mac package failed." }
# Written last so launchers never see a version before its lib.zip exists.
Set-Content "$update/version.txt" $version -Encoding ascii
Write-Host "Published client version $version"
