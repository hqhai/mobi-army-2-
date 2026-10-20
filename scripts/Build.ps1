$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$jdk = Get-ChildItem "$root/tools" -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdk) { throw 'Portable JDK not found.' }
$env:JAVA_HOME = $jdk.FullName
$env:GRADLE_USER_HOME = "$root/tools/gradle-cache"
New-Item -ItemType Directory -Path "$root/runtime/server-classes" -Force | Out-Null
$sources = @(Get-ChildItem "$root/server/src" -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
$sources += @(Get-ChildItem $PSScriptRoot -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines("$root/runtime/server-sources.txt", $sources, [Text.UTF8Encoding]::new($false))
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 --release 20 -cp "$root/server/lib/*" -d "$root/runtime/server-classes" "@$root/runtime/server-sources.txt"
if ($LASTEXITCODE -ne 0) { throw "Server compilation failed: $LASTEXITCODE" }
& "$root/client/gradlew.bat" -p "$root/desktop-build" --no-daemon --console=plain installDist
if ($LASTEXITCODE -ne 0) { throw "Client build failed: $LASTEXITCODE" }
