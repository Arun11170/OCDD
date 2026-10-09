@echo off
cd /d "%~dp0"
wsl bash -lc "source ~/et/bin/activate && python /mnt/d/OCDD/compare_models.py %*"
echo.
echo Finished. Send me compare_report.txt
pause
