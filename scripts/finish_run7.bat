@echo off
cd /d D:\OCDD
set A=app\CropAdvisor\app\src\main\assets
set OCDD_ONNX=D:\OCDD\export_v7\vision_fp32.onnx
set OCDD_INTERIM=interim_v4
set OCDD_CLASSES=D:\OCDD\export_v7\classes.json
set OCDD_TARGET=0.95
python ml\accuracy_lab.py 1500
if errorlevel 1 (echo TEST FAILED, send me the red text & pause & exit /b 1)
python tools\make_gates.py accuracy_lab_gates.json %A%\gates.json
type %A%\gates.json
echo.
echo DONE. Send me accuracy_lab_report.txt
pause
