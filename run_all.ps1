# ONE-CLICK PIPELINE. Double-click run_all.bat (or:  .\run_all.ps1).  Safe to stop and start again at any time.
#   PAUSE : double-click pause.bat  -> the running step saves after its current epoch (a few minutes) and stops.
#   STOP  : Ctrl+C or close the window -> you lose at most the epoch that was running (5-10 minutes of work).
#   RESUME: double-click run_all.bat again. Finished steps are skipped, the unfinished one continues from its last saved epoch.
#   STATUS: status.bat shows what is done and what is left.
# It uses the GPU fully and up to 90% of the CPU threads (10% stays free for you), at low priority so your own apps stay smooth.
param([switch]$Status, [switch]$SkipPreflight)
Set-Location D:\OCDD
$env:PYTHONUNBUFFERED = "1"
$env:PYTHONWARNINGS = "ignore"
New-Item -ItemType Directory -Force logs | Out-Null
New-Item -ItemType Directory -Force logs\done | Out-Null

$crops = "apple","banana","capsicum","chilli","grape","groundnut","maize","mango","okra","potato","rice","sugarcane","tomato","wheat"
$Steps = New-Object System.Collections.ArrayList
function Add-Step($name, $title, $worst, $need, [scriptblock]$run, $optional = $false) {
    [void]$Steps.Add(@{ name = $name; title = $title; worst = $worst; need = $need; run = $run; optional = $optional })
}
$wsl = "source ~/et/bin/activate && "
Add-Step "leafseg" "Leaf finder (outline of the leaf)"                 "0.6 h"  "export_seg\leafseg.onnx"       { python train_leafseg.py }
if (Test-Path "export_lesion\best_v1.pt") {
    Add-Step "eval_v1" "Score the first lesion model on healthy leaves (before/after)" "0.1 h" "export_lesion\eval_best_v1.txt" { python train_lesion.py --eval-only --ckpt=best_v1.pt } $true
}
$la = "--epochs=24"
if (Test-Path "export_lesion\best_v1.pt") { $la = "--epochs=16 --init=best_v1.pt" }    # continue from the first lesion model: about 1 hour faster
Add-Step "lesion"  "Lesion finder v2 (damaged area, with healthy leaves)" "2.5 h" "export_lesion\DONE_v2"        { python train_lesion.py @($la -split " ") }
Add-Step "run5"    "General model, all crops (EfficientNet-V2-S)"      "4 h"    "training\run5\report.txt"     { python train.py --arch efficientnet_v2_s --interim interim_v3 --epochs 20 --batch 32 --lr 5e-4 --sampling-power 0.75 --out run5 }
foreach ($c in $crops) {
    $cc = $c
    $sb = { python train.py --crop $cc --interim interim_v3 --epochs 12 --batch 64 --lr 1e-3 --sampling-power 0.75 --out "crop_$cc" }.GetNewClosure()
    Add-Step "crop_$cc" "Specialist model: $cc (+ pests)" "0.5 h" "training\crop_$cc\report.txt" $sb
}
Add-Step "export_v5"    "Export general model to ONNX (WSL)"    "0.4 h" "export_v5\onnx_report.txt" { wsl bash -lc ($wsl + "OCDD_RUN=run5 OCDD_INTERIM=interim_v3 OCDD_OUT=export_v5 OCDD_ARCH=efficientnet_v2_s python /mnt/d/OCDD/export_onnx.py") } $true
Add-Step "export_crops" "Export the 14 specialists to ONNX (WSL)"  "0.8 h" "crops_report.txt" { wsl bash -lc ($wsl + "bash /mnt/d/OCDD/export_crops.sh"); python pack_crops.py } $true
Add-Step "benchmark"    "Honest accuracy report (WSL)"          "0.4 h" "benchmark_report.txt" { wsl bash -lc ($wsl + "OCDD_ONNX=/mnt/d/OCDD/export_v5/vision_fp32.onnx OCDD_INTERIM=interim_v3 python /mnt/d/OCDD/benchmark.py") } $true

function Is-Done($step) {
    if (Test-Path "logs\done\$($step.name).ok") { return $true }
    if (Test-Path $step.need) { "ok" | Out-File "logs\done\$($step.name).ok"; return $true }
    return $false
}
function Show-Status {
    Write-Host ""; Write-Host "  STEP                                                  WORST CASE   STATE" -ForegroundColor Cyan
    $left = 0.0; $next = $true
    foreach ($step in $Steps) {
        if (Is-Done $step) { $state = "done"; $col = "Green" }
        elseif ($next) { $state = "NEXT"; $col = "Yellow"; $next = $false; $left += [double]($step.worst -replace " h", "") }
        else { $state = "waiting"; $col = "Gray"; $left += [double]($step.worst -replace " h", "") }
        Write-Host ("  {0,-54}{1,-13}{2}" -f $step.title, $step.worst, $state) -ForegroundColor $col
    }
    Write-Host ("  worst case still to run: about {0:N1} hours (typical is about 30% less)" -f $left) -ForegroundColor Cyan
    Write-Host ""
}
if ($Status) { Show-Status; exit 0 }

# --- do not start while another training is running (two trainings at once would fight for the GPU and RAM)
$busy = Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -match 'train(_lesion|_leafseg|_crops)?\.py' }
if ($busy) {
    Write-Host "Another training is still running (process $($busy[0].ProcessId)). Stop it first: click its window and press Ctrl+C, then start run_all.bat again." -ForegroundColor Red
    exit 1
}
Remove-Item D:\OCDD\PAUSE -ErrorAction SilentlyContinue
powercfg /change standby-timeout-ac 0 | Out-Null
powercfg /change hibernate-timeout-ac 0 | Out-Null
powercfg /change monitor-timeout-ac 0 | Out-Null
if (-not (Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'monitor\.ps1' -and $_.ProcessId -ne $PID })) {
    Start-Process powershell -ArgumentList "-NoExit","-ExecutionPolicy","Bypass","-File","D:\OCDD\monitor.ps1"
}
Write-Host "================ OCDD one-click pipeline   $(Get-Date -Format 'dd-MMM HH:mm') ================" -ForegroundColor Cyan
Write-Host "  PAUSE: double-click pause.bat     STOP: Ctrl+C     RESUME: run run_all.bat again     STATUS: status.bat"
Show-Status

# --- preflight: 5-minute self-test of training, pause, resume and the lesion finder on tiny data, so a bug shows up now and not at 3 a.m.
if (-not $SkipPreflight -and -not (Test-Path "logs\done\preflight.ok")) {
    Write-Host "--- preflight self-test (about 5 minutes) ---" -ForegroundColor Cyan
    $pl = "logs\overnight_preflight.txt"
    "preflight " + (Get-Date) | Out-File $pl
    "x" | Out-File D:\OCDD\PAUSE
    python train.py --arch mobilenet_v3_large --interim interim_v3 --limit 20 --epochs 2 --batch 16 --out preflight --fresh 2>&1 | Tee-Object -FilePath $pl -Append | Select-String "epoch +[0-9]|RESUMING|PAUSED|caching|workers"
    $c1 = $LASTEXITCODE
    Remove-Item D:\OCDD\PAUSE -ErrorAction SilentlyContinue
    python train.py --arch mobilenet_v3_large --interim interim_v3 --limit 20 --epochs 2 --batch 16 --out preflight 2>&1 | Tee-Object -FilePath $pl -Append | Select-String "epoch +[0-9]|RESUMING|PAUSED|caching|workers"
    $c2 = $LASTEXITCODE
    $resumed = Select-String -Path $pl -Pattern "RESUMING" -Quiet
    python train_lesion.py --smoke 2>&1 | Tee-Object -FilePath $pl -Append | Select-String "photos|epoch +[0-9]|done|Error|error"
    $c3 = $LASTEXITCODE
    Write-Host ("  train pauses cleanly: {0}   train resumes: {1}   lesion finder runs: {2}" -f ($c1 -eq 3), ($c2 -eq 0 -and $resumed), ($c3 -eq 0))
    if ($c1 -eq 3 -and $c2 -eq 0 -and $resumed -and $c3 -eq 0) { "ok" | Out-File logs\done\preflight.ok; Write-Host "  preflight passed" -ForegroundColor Green }
    else { Write-Host "  PREFLIGHT FAILED. Send me logs\overnight_preflight.txt (last 40 lines). Nothing long was started." -ForegroundColor Red; exit 2 }
}

# --- the long steps
$failed = @(); $t0 = Get-Date
foreach ($step in $Steps) {
    if (Is-Done $step) { continue }
    if (Test-Path D:\OCDD\PAUSE) { Write-Host "PAUSE file found - stopping before '$($step.name)'. Run run_all.bat to continue." -ForegroundColor Yellow; exit 3 }
    Write-Host ""; Write-Host "=============== $($step.name): $($step.title)   started $(Get-Date -Format HH:mm)   ($([int]((Get-Date) - $t0).TotalMinutes) min this session) ===============" -ForegroundColor Cyan
    $log = "logs\overnight_$($step.name).txt"
    & $step.run 2>&1 | Tee-Object -FilePath $log -Append
    $code = $LASTEXITCODE
    if ($code -eq 3) { Write-Host "PAUSED. Progress is saved. Run run_all.bat to continue." -ForegroundColor Yellow; exit 3 }
    if (Test-Path $step.need) { "ok" | Out-File "logs\done\$($step.name).ok"; Write-Host "=============== $($step.name) finished $(Get-Date -Format HH:mm) ===============" -ForegroundColor Green }
    else {
        $failed += $step.name
        Write-Host "=============== $($step.name) did NOT finish (exit code $code). See $log. Continuing with the next step; run_all.bat will retry it. ===============" -ForegroundColor Red
        if (-not $step.optional -and $step.name -match '^(leafseg|lesion|run5)$') { Start-Sleep -Seconds 5 }
    }
}
Write-Host ""
Show-Status
if ($failed.Count) { Write-Host ("Steps that failed: {0}. Send me their logs\overnight_<name>.txt (last 40 lines)." -f ($failed -join ", ")) -ForegroundColor Red }
else { Write-Host "ALL DONE. Send me: export_seg\report.txt, export_lesion\report.txt, training\run5\report.txt, benchmark_report.txt, crops_report.txt" -ForegroundColor Green }
1..5 | ForEach-Object { [console]::beep(1000,400); Start-Sleep -Milliseconds 300 }
