param([string]$Username, [switch]$Revoke)
# Makes an admin-page account a super-admin (it may also gift gear and items to players), creating it when missing,
# or with -Revoke turns it back into a plain admin. Run it on the server machine; the database must be running.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if (-not $Username) { $Username = Read-Host 'Super-admin username (3-50 letters, numbers, underscores)' }
if ($Username -notmatch '^[a-zA-Z0-9_]{3,50}$') { throw 'Invalid username.' }
$java = (Get-ChildItem "$root/tools/jdk-*/bin/java.exe" | Select-Object -First 1).FullName
$arguments = @('-Darmy.db.url=jdbc:mysql://127.0.0.1:3307/army', '-Darmy.db.user=root', '-cp', "$root/runtime/server-classes;$root/server/lib/*", 'CreateSuperAdmin', $Username)
if ($Revoke) {
    $arguments += '--revoke'
} else {
    $entry = [pscredential]::new($Username, (Read-Host 'Password for this account (8-64 characters)' -AsSecureString))
    if ($entry.Password.Length -lt 8) { throw 'Use at least 8 characters.' }
    [Environment]::SetEnvironmentVariable('ARMY_ACCOUNT_PASSWORD', $entry.GetNetworkCredential().Password, 'Process')
}
[Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', (Import-Clixml "$root/runtime/db-credential.xml").GetNetworkCredential().Password, 'Process')
try {
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Could not update the account. Check that the database is running.' }
} finally {
    [Environment]::SetEnvironmentVariable('ARMY_DB_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('ARMY_ACCOUNT_PASSWORD', $null, 'Process')
}
