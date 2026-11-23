@echo off
title OCDD - build negatives and train run7
cd /d D:\OCDD
echo ===== Step 1/2: building the "not a plant" class (about 1.5 GB download) =====
if exist data\interim_v4\manifest.csv (echo already built, skipping) else (python ml\get_negatives.py)
if not exist data\interim_v4\manifest.csv (
  echo.
  echo FAILED: data\interim_v4\manifest.csv was not created. Check the internet and run this file again.
  pause
  exit /b 1
)
echo.
echo ===== Step 2/2: training run7 (leave the laptop plugged in; it resumes if interrupted) =====
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\OCDD\scripts\run_v7.ps1"
echo.
echo ALL DONE. Send me D:\OCDD\training\run7\report.txt
pause
