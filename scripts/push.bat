@echo off
cd /d D:\OCDD
del .git\index.lock 2>nul
git push origin main
echo.
echo Next: GitHub - Settings - General - Default branch -> main
pause
