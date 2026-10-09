# Live "is it running?" window. Opened automatically by run_overnight.ps1 / train_crops.ps1, or run alone:  .\monitor.ps1
while ($true) {
  Clear-Host
  Write-Host "OCDD training monitor   $(Get-Date -Format 'dd-MMM HH:mm:ss')   (Ctrl+C closes this window only)" -ForegroundColor Cyan
  $g = & nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw --format=csv,noheader,nounits 2>$null
  if ($g) { $p = $g.Split(","); Write-Host ("GPU   busy {0}%   memory {1} / {2} MiB   {3} C   {4} W" -f $p[0].Trim(),$p[1].Trim(),$p[2].Trim(),$p[3].Trim(),$p[4].Trim()) }
  $os = Get-CimInstance Win32_OperatingSystem
  $usedGB = [math]::Round(($os.TotalVisibleMemorySize - $os.FreePhysicalMemory)/1MB,1); $totGB = [math]::Round($os.TotalVisibleMemorySize/1MB,1)
  $cpu = [math]::Round((Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average,0)
  Write-Host "CPU   busy $cpu%      RAM used $usedGB / $totGB GB"
  $done = @(Get-ChildItem D:\OCDD\logs\done\*.ok -ErrorAction SilentlyContinue).Count
  Write-Host "steps finished so far: $done   (status.bat shows the full list)"
  if (Test-Path D:\OCDD\PAUSE) { Write-Host "PAUSE REQUESTED - the running step will stop after its current epoch" -ForegroundColor Yellow }
  $py = Get-Process python -ErrorAction SilentlyContinue
  if ($py) { Write-Host ("python processes running: {0}   (training uses 1 main + several workers)" -f $py.Count) -ForegroundColor Green }
  else { Write-Host "NO python process running  -> training is not running (finished or stopped)" -ForegroundColor Yellow }
  $log = Get-ChildItem D:\OCDD\logs\overnight_*.txt, D:\OCDD\logs\crop_*.txt -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($log) {
    $age = [int]((Get-Date) - $log.LastWriteTime).TotalSeconds
    Write-Host ("log: {0}   last written {1} s ago" -f $log.Name, $age)
    if ($age -gt 300 -and $py) { Write-Host "  (no new log line for 5+ minutes: normal during validation/export, but tell me if it lasts 20+ min)" -ForegroundColor Yellow }
    Write-Host "---- last lines ----"; Get-Content $log.FullName -Tail 4
  }
  Start-Sleep -Seconds 5
}
