@echo off
cd /d "%~dp0"
wsl bash -lc "source ~/et/bin/activate && python /mnt/d/OCDD/test_100.py"
echo.
echo Finished. Send me test100_report.txt
pause
