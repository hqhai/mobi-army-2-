$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
New-Item -ItemType File -Path "$root/runtime/stop-server" -Force | Out-Null
if (Test-Path "$root/runtime/server.pid") {
    $serverProcessId = [int](Get-Content "$root/runtime/server.pid")
    $process = Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue
    if ($process -and $process.Path.StartsWith("$root\tools\", [StringComparison]::OrdinalIgnoreCase)) {
        if (-not $process.WaitForExit(30000)) { throw 'Server has not stopped; database left running to preserve data.' }
    }
}
$maria = (Get-ChildItem "$root/tools/mariadb-*-winx64" -Directory | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable('MYSQL_PWD', (Import-Clixml "$root/runtime/db-credential.xml").GetNetworkCredential().Password, 'Process')
try {
    & "$maria/bin/mariadb-admin.exe" --host=127.0.0.1 --port=3307 --user=root shutdown
    if ($LASTEXITCODE -ne 0) { throw 'Database shutdown failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
}
Write-Host 'Game server stopped and database shut down.'
