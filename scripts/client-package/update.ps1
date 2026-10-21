param([string]$Server = '10.10.0.172:8123')
# Downloads the newest client lib from the game web server. Any failure keeps the current files.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$dir = $PSScriptRoot
$base = "http://$Server/downloads/update"
try {
    $content = (Invoke-WebRequest "$base/version.txt" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -is [byte[]]) { $content = [Text.Encoding]::ASCII.GetString($content) }
    $remote = $content.Trim()
    $localFile = Join-Path $dir 'version.txt'
    $local = if (Test-Path $localFile) { (Get-Content $localFile -Raw).Trim() } else { '' }
    if (-not $remote -or $remote -eq $local) { return }
    Write-Host "Dang cap nhat game len ban $remote ..."
    $zip = Join-Path $env:TEMP 'mobiarmy2-lib.zip'
    $new = Join-Path $dir 'lib.new'
    Invoke-WebRequest "$base/lib.zip" -UseBasicParsing -OutFile $zip -TimeoutSec 300
    if (Test-Path $new) { Remove-Item $new -Recurse -Force }
    Expand-Archive $zip $new -Force
    Remove-Item $zip -Force
    if (-not (Get-ChildItem $new -Filter *.jar)) { throw 'Invalid update package.' }
    $old = Join-Path $dir 'lib'
    if (Test-Path $old) { Remove-Item $old -Recurse -Force }
    Rename-Item $new 'lib'
    Set-Content $localFile $remote -Encoding ascii
    Write-Host 'Cap nhat xong.'
} catch {
    Write-Host "Khong cap nhat duoc ($($_.Exception.Message)). Choi bang ban hien tai."
}
