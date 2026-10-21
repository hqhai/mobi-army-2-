$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$java = (Get-ChildItem "$root/tools/jdk-*/bin/java.exe" | Select-Object -First 1).FullName
$maria = (Get-ChildItem "$root/tools/mariadb-*-winx64" -Directory | Select-Object -First 1).FullName
$existing = Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue
if (-not $existing) {
    $db = Start-Process "$maria/bin/mariadbd.exe" -ArgumentList @(
        "--defaults-file=$root/runtime/database/my.ini", '--bind-address=127.0.0.1', '--port=3307', '--console'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/logs/database.out.log" -RedirectStandardError "$root/logs/database.err.log"
    $db.Id | Set-Content "$root/runtime/database.pid"
    for ($i = 0; $i -lt 30; $i++) {
        if (Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue) { break }
        Start-Sleep -Seconds 1
    }
} elseif ((Get-Process -Id $existing[0].OwningProcess).Path -ne "$maria\bin\mariadbd.exe") {
    throw 'Port 3307 is occupied by another database.'
}
if (Get-NetTCPConnection -State Listen -LocalPort 8122 -ErrorAction SilentlyContinue) {
    Write-Host 'Port 8122 is already listening. Server not started a second time.'
    return
}
if (Test-Path "$root/runtime/stop-server") { Remove-Item -LiteralPath "$root/runtime/stop-server" }
[Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', (Import-Clixml "$root/runtime/db-credential.xml").GetNetworkCredential().Password, 'Process')
try {
    $server = Start-Process $java -WorkingDirectory "$root/server" -ArgumentList @(
        '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army',
        '-Darmy.db.user=root',
        '-Darmy.bot.count=0',
        '-Dstdout.encoding=UTF-8',
        '-Dstderr.encoding=UTF-8',
        '-Darmy.web.port=8123',
        "-Darmy.web.root=$root/web",
        "-Darmy.stopFile=$root/runtime/stop-server",
        '-cp', "$root/runtime/server-classes;$root/server/lib/*", 'LanServer'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/logs/server.out.log" -RedirectStandardError "$root/logs/server.err.log"
    $server.Id | Set-Content "$root/runtime/server.pid"
} finally {
    [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', $null, 'Process')
}
for ($i = 0; $i -lt 30; $i++) {
    if (Get-NetTCPConnection -State Listen -LocalPort 8122 -ErrorAction SilentlyContinue) {
        Write-Host 'MobiArmy 2 server is listening on TCP 8122.'
        if (Get-NetTCPConnection -State Listen -LocalPort 8123 -ErrorAction SilentlyContinue) {
            $addresses = @(Get-NetIPConfiguration | Where-Object IPv4DefaultGateway | ForEach-Object { $_.IPv4Address.IPAddress })
            if (-not $addresses) { $addresses = @('<server-ip>') }
            foreach ($address in $addresses) { Write-Host "Registration page: http://${address}:8123/" }
        } else {
            Write-Host "Registration page is not running. See $root/logs/server.err.log"
        }
        return
    }
    if ($server.HasExited) { throw "Game server exited. See $root/logs/server.err.log" }
    Start-Sleep -Seconds 1
}
throw 'Game server did not start within 30 seconds. Check logs.'
