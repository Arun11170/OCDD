@echo off
title Make ONE zip with everything Kaggle needs
cd /d D:\OCDD
if not exist data\interim\cache256 ( echo cache256 folder not found & pause & exit /b 1 )
if not exist data\interim_v4\manifest.csv ( echo data\interim_v4\manifest.csv not found - run get_negatives.py first & pause & exit /b 1 )
set OUT=D:\OCDD\kaggle_upload
if not exist "%OUT%" mkdir "%OUT%"
echo Collecting the latest small files...
if not exist kaggle mkdir kaggle
copy /Y data\interim_v4\manifest.csv kaggle\manifest_v4.csv >nul
copy /Y train.py kaggle\ >nul
copy /Y ctl.py kaggle\ >nul
copy /Y progress.py kaggle\ >nul
if exist "%OUT%\ocdd-data.zip" del "%OUT%\ocdd-data.zip"
echo.
echo Zipping the 111,000 resized photos + manifest + training code into ONE file. 10 to 30 minutes, low priority. Do not close this window.
start "" /low /wait tar -a -c -f "%OUT%\ocdd-data.zip" -C D:\OCDD\data\interim cache256 -C D:\OCDD\kaggle manifest_v4.csv train.py ctl.py progress.py
if not exist "%OUT%\ocdd-data.zip" ( echo ZIP FAILED - tell me the message above & pause & exit /b 1 )
copy /Y kaggle\train_run7_effnet.ipynb "%OUT%\" >nul
copy /Y kaggle\train_run8_convnext.ipynb "%OUT%\" >nul
echo.
echo DONE. Everything is in %OUT%
dir "%OUT%"
echo.
echo 1) kaggle.com - Datasets - New Dataset - upload ocdd-data.zip, name it ocdd-data, Private.
echo 2) Kaggle - Code - New Notebook - File - Import Notebook - train_run7_effnet.ipynb (MAIN) and optionally train_run8_convnext.ipynb - GPU on, Add Input = ocdd-data, Save Version - Save and Run All.
pause
