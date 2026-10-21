@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Hero-Effects-Client.ps1" %*
if errorlevel 1 pause
