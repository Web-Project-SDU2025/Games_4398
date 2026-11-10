@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" %*
set "startupExitCode=%errorlevel%"
if %startupExitCode% gtr 0 pause
exit /b %startupExitCode%
