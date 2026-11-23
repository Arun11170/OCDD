@echo off
title OCDD - apply run7 to the app
cd /d D:\OCDD
if not exist training\run7\report.txt (
  echo Training has not finished yet: training\run7\report.txt does not exist. Run this after run7 is DONE.
  pause & exit /b 1
)
set A=app\CropAdvisor\app\src\main\assets
echo ===== 1/5 backing up the current model to models\backup_run5 =====
if not exist models\backup_run5 mkdir models\backup_run5
if not exist models\backup_run5\vision.onnx copy /Y %A%\vision.onnx models\backup_run5\ >nul
if not exist models\backup_run5\classes.json copy /Y %A%\classes.json models\backup_run5\ >nul
if not exist models\backup_run5\gates.json copy /Y %A%\gates.json models\backup_run5\ >nul
echo ===== 2/5 exporting run7 to ONNX (inside WSL) =====
wsl bash -lc "source ~/et/bin/activate && cd /mnt/d/OCDD && OCDD_RUN=run7 OCDD_INTERIM=interim_v4 OCDD_OUT=export_v7 OCDD_ARCH=efficientnet_v2_s OCDD_SKIP_INT8=1 python ml/export_onnx.py"
if not exist export_v7\vision_fp32.onnx (
  echo EXPORT FAILED. Copy the red text above and send it to me.
  pause & exit /b 1
)
copy /Y training\run7\classes.json export_v7\classes.json >nul
echo ===== 3/5 testing the new model on 1500 held-out photos (target 95 percent when it answers) =====
set OCDD_ONNX=D:\OCDD\export_v7\vision_fp32.onnx
set OCDD_INTERIM=interim_v4
set OCDD_CLASSES=D:\OCDD\export_v7\classes.json
set OCDD_TARGET=0.95
python ml\accuracy_lab.py 1500
if errorlevel 1 (
  echo TEST FAILED - the model was NOT installed. Send me the red text above.
  pause & exit /b 1
)
echo ===== 4/5 setting the confidence gates and installing the model =====
python tools\make_gates.py accuracy_lab_gates.json %A%\gates.json
copy /Y export_v7\vision_fp32.onnx %A%\vision.onnx >nul
copy /Y export_v7\classes.json %A%\classes.json
if errorlevel 1 (echo CLASS FILE COPY FAILED & pause & exit /b 1)
echo ===== 5/5 done =====
echo Send me: training\run7\report.txt and accuracy_lab_report.txt. Then rebuild the app in Android Studio.
echo To go back to the old model: copy the 3 files from models\backup_run5 into %A%.
pause
