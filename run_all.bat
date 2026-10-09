@echo off
cd /d D:\OCDD
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\OCDD\run_all.ps1" %*
echo.
pause
