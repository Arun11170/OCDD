@echo off
REM Builds the v2 model (more crops + pests) WITHOUT touching the working v1 model or the app.
REM  1) prepare_data.py --v2 --rice   -> D:\OCDD\data\interim_v2\manifest.csv + audit_report.txt
REM  2) train.py --interim interim_v2 --out run2   -> D:\OCDD\training\run2\
REM  3) export to ExecuTorch in WSL    -> D:\OCDD\export_v2\
REM Logs: D:\OCDD\logs\run_v2_*.txt
cd /d D:\OCDD
if not exist logs mkdir logs
echo [1/3] preparing data (hashing every image, takes a while) ...
powershell -NoProfile -Command "python prepare_data.py --v2 --rice 2>&1 | Tee-Object -FilePath logs\run_v2_prepare.txt"
if not exist data\interim_v2\manifest.csv (echo PREPARE FAILED, see logs\run_v2_prepare.txt & pause & exit /b 1)
echo [2/3] training (1-3 hours on the RTX 3050) ...
powershell -NoProfile -Command "python train.py --interim interim_v2 --out run2 --epochs 15 2>&1 | Tee-Object -FilePath logs\run_v2_train.txt"
if not exist training\run2\temperature.json (echo TRAINING FAILED, see logs\run_v2_train.txt & pause & exit /b 1)
echo [3/3] exporting to ExecuTorch in WSL ...
powershell -NoProfile -Command "wsl bash -lc 'source ~/et/bin/activate && cd /mnt/d/OCDD && OCDD_RUN=run2 OCDD_INTERIM=interim_v2 OCDD_OUT=export_v2 python export_vision.py' 2>&1 | Tee-Object -FilePath logs\run_v2_export.txt"
echo.
echo DONE. Send Claude: data\interim_v2\audit_report.txt, training\run2\report.txt, export_v2\export_report.txt
pause
