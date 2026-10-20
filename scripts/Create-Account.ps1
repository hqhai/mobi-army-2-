param([string]$Username)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if (-not $Username) { $Username = Read-Host 'Username (3-20 letters, numbers, underscores)' }
if ($Username -notmatch '^[a-zA-Z0-9_]{3,20}$') { throw 'Invalid username.' }
$entry = [pscredential]::new($Username, (Read-Host 'New account secret (at least 6 characters)' -AsSecureString))
if ($entry.Password.Length -lt 6) { throw 'Use at least 6 characters.' }
$java = (Get-ChildItem "$root/tools/jdk-*/bin/java.exe" | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', (Import-Clixml "$root/runtime/db-credential.xml").GetNetworkCredential().Password, 'Process')
[Environment]::SetEnvironmentVariable('ARMY_ACCOUNT_PASSWORD', $entry.GetNetworkCredential().Password, 'Process')
try {
    & $java '-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army' '-Darmy.db.user=root' `
        -cp "$root/runtime/server-classes;$root/server/lib/*" CreateAccount $Username
    if ($LASTEXITCODE -ne 0) { throw 'Could not create account. Check server/database is running and username is not already used.' }
} finally {
    [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('ARMY_ACCOUNT_PASSWORD', $null, 'Process')
}
