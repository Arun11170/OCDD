# Overnight experiment: does ConvNeXt-Tiny beat the shipped EfficientNet-V2-S (run5) on the SAME data and the SAME test split?
# Run in PowerShell:   cd D:\OCDD ; .\run_better.ps1        (laptop plugged in; the RTX 3050 6 GB is enough, batch 32 with mixed precision)
# If it runs out of GPU memory, edit --batch 32 to --batch 24.
# Afterwards compare  training\run6\report.txt  with  training\run5\report.txt  (look at "TEST accuracy" and "domain field").
# Only if run6 is clearly better on field photos do we export it and replace the app's model.
Set-Location D:\OCDD
New-Item -ItemType Directory -Force logs | Out-Null
$env:PYTHONUNBUFFERED = "1"
powercfg /change standby-timeout-ac 0 | Out-Null
powercfg /change hibernate-timeout-ac 0 | Out-Null
python train.py --arch convnext_tiny --interim interim_v3 --epochs 20 --batch 32 --lr 2e-4 --sampling-power 0.75 --out run6 2>&1 | Tee-Object -FilePath logs\run6.txt
Write-Host "DONE. Send me training\run6\report.txt" -ForegroundColor Green
1..5 | ForEach-Object { [console]::beep(1000,400); Start-Sleep -Milliseconds 300 }
