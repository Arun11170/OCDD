# Trains one small specialist per crop (crop classes + pest classes). Run from D:\OCDD:   .\train_crops.ps1
# Then, in WSL:  bash /mnt/d/OCDD/export_crops.sh      then (Windows):  python pack_crops.py
Set-Location D:\OCDD
$env:PYTHONUNBUFFERED = "1"
powercfg /change standby-timeout-ac 0 | Out-Null
powercfg /change hibernate-timeout-ac 0 | Out-Null
Start-Process powershell -ArgumentList "-NoExit","-ExecutionPolicy","Bypass","-File","D:\OCDD\monitor.ps1"
$crops = "apple","banana","capsicum","chilli","grape","groundnut","maize","mango","okra","potato","rice","sugarcane","tomato","wheat"
New-Item -ItemType Directory -Force logs | Out-Null
$t0 = Get-Date; $i = 0
foreach ($c in $crops) {
  $i++
  Write-Host ""; Write-Host "=== crop $i of $($crops.Count): $c   started $(Get-Date -Format HH:mm)   ($([int]((Get-Date) - $t0).TotalMinutes) min so far) ===" -ForegroundColor Cyan
  python train.py --crop $c --interim interim_v3 --epochs 12 --batch 64 --lr 1e-3 --sampling-power 0.75 --out "crop_$c" 2>&1 | Tee-Object -FilePath "logs\crop_$c.txt"
}
Write-Host "ALL CROPS DONE after $([int]((Get-Date) - $t0).TotalMinutes) minutes." -ForegroundColor Green
1..5 | ForEach-Object { [console]::beep(1000,400); Start-Sleep -Milliseconds 300 }
