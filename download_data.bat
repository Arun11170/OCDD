@echo off
cd /d "%~dp0"
echo Downloading extra photo sets into data\incoming ...
python download_data.py %*
echo.
echo Finished. Close this window or press a key.
pause
