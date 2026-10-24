param(
    [ValidateSet('pro', 'skilled', 'casual', 'exact', 'all')]
    [string]$Aim = 'all',
    [int]$Count = 2250,
    [string[]]$Extra = @()
)
# Builds the server code with scripts/sim/DuelSim.java into runtime/sim-classes and runs 1v1 duels between all
# characters (see DuelSim.java). Needs the database running (Start-Server.cmd starts it).
#
# Every profile plays free rules ("Tự do") with level 254 characters (759 points split like the top real player:
# HP 200, attack 330, defense 80, luck 149) in a full gold +14 set; the profiles differ in how well the bots aim.
# Characters' attack comes from the database; override one with e.g. -Extra att0=375,att6=470.
#   .\scripts\sim\Run-DuelSim.ps1                       all four profiles, 2250 duels each (about 5 minutes each)
#   .\scripts\sim\Run-DuelSim.ps1 -Aim skilled -Count 900 -Extra matrix=true
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot)
$jdk = "$root/tools/jdk-21/bin"
$out = "$root/runtime/sim-classes"
New-Item -ItemType Directory -Force $out | Out-Null
$sources = @(Get-ChildItem "$root/server/src" -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
$sources += '"' + "$PSScriptRoot/DuelSim.java".Replace('\', '/') + '"'
[IO.File]::WriteAllLines("$root/runtime/sim-sources.txt", $sources, [Text.UTF8Encoding]::new($false))
& "$jdk/javac.exe" -encoding UTF-8 --release 21 -nowarn -cp "$root/server/lib/*" -d $out "@$root/runtime/sim-sources.txt"
if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $LASTEXITCODE" }

$full = @('rules=0', 'hp=200', 'att=330', 'def=80', 'luck=149')
$profiles = [ordered]@{
    'pro'     = $full + @('sf=0.3', 'sa=0.5', 's2=0.5', 'sw=0.5', 'evade=0.7') # small hand error, usually steps off eggs
    'skilled' = $full + @('sf=0.6', 'sa=1.0', 's2=1.0', 'sw=1.0', 'evade=0.5')
    'casual'  = $full + @('sf=1.5', 'sa=3', 's2=2', 'sw=1.5', 'evade=0.3')
    'exact'   = $full + @('sf=0', 'sa=0', 's2=0', 'sw=0', 'evade=0')         # the in-game Bot's perfect aim
}
$extraArgs = @($Extra | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
[Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', (Import-Clixml "$root/runtime/db-credential.xml").GetNetworkCredential().Password, 'Process')
try {
    Push-Location "$root/server" # bricks and hole masks are read from server/res
    foreach ($name in $profiles.Keys) {
        if ($Aim -ne 'all' -and $Aim -ne $name) { continue }
        "== $name"
        & "$jdk/java.exe" -Xmx6g '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn' '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' '-Darmy.db.user=root' `
            -cp "$out;$root/server/lib/*" mobiarmy.war.DuelSim "matches=$Count" @($profiles[$name] + $extraArgs)
        if ($LASTEXITCODE -ne 0) { throw "Simulation failed: $LASTEXITCODE" }
    }
} finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', $null, 'Process')
}
