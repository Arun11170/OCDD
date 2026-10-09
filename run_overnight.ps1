# One-command overnight run. In PowerShell:   cd D:\OCDD ; .\run_overnight.ps1
# Before you sleep: plug the laptop in. This also stops Windows sleeping while plugged in.
# Order: 1 download PlantSeg  2 leaf finder  3 lesion finder  4 disease+pest model (EfficientNet-V2-S)
# A second window (monitor) shows GPU / CPU / RAM and the newest log lines every 5 seconds, so you can see it is alive.
# Every step writes a log in D:\OCDD\logs\ ; if one step fails the next still runs.
Set-Location D:\OCDD
New-Item -ItemType Directory -Force logs | Out-Null
$env:PYTHONUNBUFFERED = "1"            # show output immediately, not in big delayed chunks
powercfg /change standby-timeout-ac 0 | Out-Null
powercfg /change hibernate-timeout-ac 0 | Out-Null
powercfg /change monitor-timeout-ac 0 | Out-Null     # screen stays on so you can see it (turn the brightness down)
Start-Process powershell -ArgumentList "-NoExit","-ExecutionPolicy","Bypass","-File","D:\OCDD\monitor.ps1"
$t0 = Get-Date
function Step($name, $cmd) {
    Write-Host ""; Write-Host "=============== $name   started $(Get-Date -Format HH:mm)   (running for $([int]((Get-Date) - $t0).TotalMinutes) min so far) ===============" -ForegroundColor Cyan
    $p = Get-Process -Id $PID; try { $p.PriorityClass = "High" } catch {}
    Invoke-Expression "$cmd 2>&1 | Tee-Object -FilePath logs\overnight_$name.txt"
    Write-Host "=============== $name finished $(Get-Date -Format HH:mm) ===============" -ForegroundColor Green
}
if (-not (Test-Path "D:\OCDD\data\raw\plantseg\data")) { Step "data" "python get_data.py plantseg" } else { Write-Host "PlantSeg already downloaded - skipping data step" -ForegroundColor Yellow }
Step "leafseg" "python train_leafseg.py"
Step "lesion"  "python train_lesion.py"
Step "run5"    "python train.py --arch efficientnet_v2_s --interim interim_v3 --epochs 20 --batch 32 --lr 5e-4 --sampling-power 0.75 --out run5"
Write-Host "ALL DONE after $([int]((Get-Date) - $t0).TotalMinutes) minutes. Send me: export_seg\report.txt, export_lesion\report.txt, training\run5\report.txt" -ForegroundColor Green
1..5 | ForEach-Object { [console]::beep(1000,400); Start-Sleep -Milliseconds 300 }
