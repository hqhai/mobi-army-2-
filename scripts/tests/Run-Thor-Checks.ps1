$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot)
Push-Location $root
try {
    & "$root/scripts/Build.ps1"
    $java = "$root/tools/jdk-21/bin/java.exe"
    $javac = "$root/tools/jdk-21/bin/javac.exe"
    New-Item -ItemType Directory -Force verification, verification/tmp, verification/profile | Out-Null
    & $javac -encoding UTF-8 -cp 'runtime/server-classes;server/lib/*' -d verification scripts/tests/ThorShopCheck.java scripts/tests/ApacheMechanicsCheck.java scripts/tests/SkillBalanceCheck.java scripts/tests/ClientVersionCheck.java scripts/tests/WindCheck.java scripts/tests/UpgradeTableCheck.java scripts/tests/SetPassiveCheck.java scripts/tests/ApacheHuntCheck.java scripts/tests/ThorCostumeCheck.java scripts/tests/CostumeBulletsCheck.java scripts/tests/RoomStartCheck.java scripts/tests/AdminRoleCheck.java scripts/tests/MapScaleCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Server test compilation failed.' }
    [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', (Import-Clixml runtime/db-credential.xml).GetNetworkCredential().Password, 'Process')
    try {
        Push-Location server
        & $java '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' -cp '../verification;../runtime/server-classes;lib/*' ThorShopCheck '../verification/thor-catalog.bin' *> ../verification/thor-shop.log
        if ($LASTEXITCODE -ne 0) { throw 'Shop/persistence checks failed; see verification/thor-shop.log.' }
        & $java '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' -cp '../verification;../runtime/server-classes;lib/*' ThorCostumeCheck *> ../verification/thor-costume.log
        if ($LASTEXITCODE -ne 0) { throw 'Thor costume checks failed; see verification/thor-costume.log.' }
        & $java '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' -cp '../verification;../runtime/server-classes;lib/*' CostumeBulletsCheck *> ../verification/costume-bullets.log
        if ($LASTEXITCODE -ne 0) { throw 'Costume bullet checks failed; see verification/costume-bullets.log.' }
        & $java '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' -cp '../verification;../runtime/server-classes;lib/*' AdminRoleCheck *> ../verification/admin-role.log
        if ($LASTEXITCODE -ne 0) { throw 'Admin role checks failed; see verification/admin-role.log.' }
        & $java -cp '../verification;../runtime/server-classes;lib/*' MapScaleCheck
        if ($LASTEXITCODE -ne 0) { throw 'Map scale checks failed.' }
    } finally {
        Pop-Location
        [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', $null, 'Process')
    }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' ApacheMechanicsCheck
    if ($LASTEXITCODE -ne 0) { throw 'Apache mechanics failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' SkillBalanceCheck
    if ($LASTEXITCODE -ne 0) { throw 'Skill balance checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' ClientVersionCheck
    if ($LASTEXITCODE -ne 0) { throw 'Client version checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' WindCheck
    if ($LASTEXITCODE -ne 0) { throw 'Wind checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' UpgradeTableCheck
    if ($LASTEXITCODE -ne 0) { throw 'Upgrade table checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' mobiarmy.war.SetPassiveCheck
    if ($LASTEXITCODE -ne 0) { throw 'Set passive checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' mobiarmy.war.ApacheHuntCheck
    if ($LASTEXITCODE -ne 0) { throw 'Apache hunt checks failed.' }
    & $java -cp 'verification;runtime/server-classes;server/lib/*' RoomStartCheck
    if ($LASTEXITCODE -ne 0) { throw 'Room start checks failed.' }
    $clientCp = 'verification;desktop-build/build/install/army2-lan-desktop/lib/*'
    & $javac -encoding UTF-8 -cp $clientCp -d verification scripts/tests/ThorSkinCheck.java scripts/tests/ThorSkinDisabledCheck.java scripts/tests/ThorShopUiCheck.java scripts/tests/KeyboardControlsCheck.java scripts/tests/RoomRulesUiCheck.java scripts/tests/InventorySyncCheck.java scripts/tests/EquipDataReloadCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Client test compilation failed.' }
    $vm = @("-Duser.home=$root/verification/profile", "-Djava.io.tmpdir=$root/verification/tmp", "-Dthor.catalog=$root/verification/thor-catalog.bin", "-Dthor.preview=$root/art/concepts/apache-thor/runtime-shop-walk-preview.png", "-Dthor.shopPreview=$root/art/concepts/apache-thor/shop-preview.png")
    foreach ($check in @('ThorSkinDisabledCheck', 'player.ThorSkinCheck', 'player.ThorShopUiCheck', 'screen.KeyboardControlsCheck', 'screen.RoomRulesUiCheck', 'screen.InventorySyncCheck', 'EquipDataReloadCheck')) {
        $log = "$root/verification/$check.log"
        & $java @vm -cp $clientCp $check *> $log
        if ($LASTEXITCODE -ne 0) { Get-Content $log -Tail 20; throw "$check failed." }
        Get-Content $log -Tail 1
    }
    Get-Content verification/thor-shop.log -Tail 1
} finally { Pop-Location }
