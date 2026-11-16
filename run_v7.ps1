# run7 = same recipe as the shipped run5 (EfficientNet-V2-S) PLUS the new class none__not_plant (interim_v4).
# 1) python get_negatives.py     2) .\run_v7.ps1      (laptop plugged in; ~same time as run5)
Set-Location D:\OCDD
New-Item -ItemType Directory -Force logs | Out-Null
$env:PYTHONUNBUFFERED = "1"
powercfg /change standby-timeout-ac 0 | Out-Null
powercfg /change hibernate-timeout-ac 0 | Out-Null
python train.py --arch efficientnet_v2_s --interim interim_v4 --epochs 20 --batch 32 --lr 5e-4 --sampling-power 0.75 --workers 3 --out run7 2>&1 | Tee-Object -FilePath logs\run7.txt
Write-Host "DONE. Send me training\run7\report.txt" -ForegroundColor Green
1..5 | ForEach-Object { [console]::beep(1000,400); Start-Sleep -Milliseconds 300 }
