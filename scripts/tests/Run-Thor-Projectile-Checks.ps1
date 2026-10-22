$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot)
Push-Location $root
try {
    $env:JAVA_HOME = "$root/tools/jdk-21"
    $env:GRADLE_USER_HOME = "$root/tools/gradle-cache"
    & ./client/gradlew.bat -p desktop-build --no-daemon --console=plain classes
    if ($LASTEXITCODE -ne 0) { throw 'Client build failed.' }
    New-Item -ItemType Directory -Force verification/thor-projectiles, verification/tmp, verification/profile | Out-Null
    $libs = "$root/runtime/thor-projectiles-v2/army2-lan-desktop/lib/*"
    if (!(Test-Path "$root/runtime/thor-projectiles-v2/army2-lan-desktop/lib")) {
        $libs = "$root/desktop-build/build/install/army2-lan-desktop/lib/*"
    }
    $cp = "$root/desktop-build/build/classes/java/main;$root/client/desktop/assets;$libs;$root/verification/thor-projectiles"
    & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d verification/thor-projectiles scripts/tests/ThorProjectilesCheck.java scripts/tests/ThorProjectilesRenderCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Projectile test compilation failed.' }
    & "$env:JAVA_HOME/bin/java.exe" -cp $cp player.ThorProjectilesCheck client/desktop/assets/skins/thor-classes/projectiles.json
    if ($LASTEXITCODE -ne 0) { throw 'Projectile identity/render bounds checks failed.' }
    & "$env:JAVA_HOME/bin/java.exe" "-Duser.home=$root/verification/profile" "-Djava.io.tmpdir=$root/verification/tmp" "-Dthor.preview=$root/art/concepts/thor-all-classes/thor-projectiles-runtime-v2.png" -cp $cp player.ThorProjectilesRenderCheck
    if ($LASTEXITCODE -ne 0) { throw 'Projectile runtime rendering checks failed.' }
} finally { Pop-Location }
