@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0update.ps1" -Server 10.10.0.172:8123
if not exist "%~dp0data" mkdir "%~dp0data"
start "" "%~dp0jre\bin\javaw.exe" -Darmy.host=10.10.0.172 -Darmy.port=8122 "-Duser.home=%~dp0data" -cp "%~dp0lib\*" com.mygdx.game.DesktopLauncher
