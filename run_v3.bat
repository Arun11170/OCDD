@echo off
REM v3 = v2 data, but pests that look alike are merged into farmer-actionable groups, 25 epochs, stronger boost for rare classes, better int8 calibration.
REM Re-runs prepare (v2 data + okra yellow-vein-mosaic photos) first, then trains. Output: training\run3 and export_v3. v1 and v2 stay untouched.
cd /d D:\OCDD
if not exist logs mkdir logs
echo [1/3] preparing data (about 20-40 min, reads all images once) ...
powershell -NoProfile -Command "python prepare_data.py --v2 --rice 2>&1 | Tee-Object -FilePath logs\run_v3_prepare.txt"
if not exist data\interim_v2\manifest.csv (echo PREPARE FAILED, see logs\run_v3_prepare.txt & pause & exit /b 1)
echo [2/3] training 25 epochs (about 1.2 hours) ...
powershell -NoProfile -Command "python train.py --interim interim_v2 --out run3 --epochs 25 --sampling-power 0.75 2>&1 | Tee-Object -FilePath logs\run_v3_train.txt"
if not exist training\run3\temperature.json (echo TRAINING FAILED, see logs\run_v3_train.txt & pause & exit /b 1)
echo [3/3] exporting ...
powershell -NoProfile -Command "wsl bash -lc 'source ~/et/bin/activate && cd /mnt/d/OCDD && OCDD_RUN=run3 OCDD_INTERIM=interim_v2 OCDD_OUT=export_v3 python export_vision.py' 2>&1 | Tee-Object -FilePath logs\run_v3_export.txt"
echo.
echo DONE. Send Claude: training\run3\report.txt and export_v3\export_report.txt
pause
