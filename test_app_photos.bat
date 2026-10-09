@echo off
cd /d "%~dp0"
wsl bash -lc "source ~/et/bin/activate && python /mnt/d/OCDD/test_app_photos.py"
echo.
echo Finished. Send me app_test_report.txt
pause
