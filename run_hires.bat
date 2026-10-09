@echo off
cd /d "%~dp0"
echo ===== 320px model for rice, okra, chilli, groundnut (about 1 hour). Pause: pause.bat   Resume: run this file again =====
if exist PAUSE del PAUSE
if not exist logs\done mkdir logs\done
if exist logs\done\hires_smoke.ok goto train
echo --- quick self-test (2-3 minutes) ---
python train.py --crops rice,okra,chilli,groundnut --no-pests --img 320 --arch efficientnet_v2_s --interim interim_v3 --epochs 1 --batch 16 --limit 80 --out hires_smoke --fresh
if errorlevel 1 ( echo SELF-TEST FAILED - send me the text above & pause & exit /b 1 )
echo ok> logs\done\hires_smoke.ok
:train
if exist logs\done\hires_train.ok goto export
python train.py --crops rice,okra,chilli,groundnut --no-pests --img 320 --arch efficientnet_v2_s --interim interim_v3 --epochs 14 --batch 32 --lr 5e-4 --sampling-power 0.75 --out hires4
if errorlevel 3 ( echo PAUSED - run run_hires.bat again to continue & exit /b 3 )
if errorlevel 1 ( echo TRAINING FAILED - send me the text above & pause & exit /b 1 )
echo ok> logs\done\hires_train.ok
:export
wsl bash -lc "source ~/et/bin/activate && OCDD_RUN=hires4 OCDD_INTERIM=interim_v3 OCDD_OUT=export_hires OCDD_ARCH=efficientnet_v2_s OCDD_IMG=320 OCDD_SKIP_INT8=1 python /mnt/d/OCDD/export_onnx.py"
if errorlevel 1 ( echo EXPORT FAILED - send me the text above & pause & exit /b 1 )
wsl bash -lc "source ~/et/bin/activate && python /mnt/d/OCDD/compare_hires.py"
echo.
echo ===== FINISHED. Send me compare_hires_report.txt =====
pause
