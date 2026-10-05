# Extracts the 3 .rar files inside data\raw\chilli into one folder each, deletes each rar after success.
# Run:  powershell -ExecutionPolicy Bypass -File D:\OCDD\extract_chilli.ps1
$base = "D:\OCDD\data\raw\chilli\chilli dataset"
$log = "D:\OCDD\logs\datasets_log.txt"
function Log($m) { $l = "{0}  {1}" -f (Get-Date -Format s), $m; Write-Host $l; Add-Content -Path $log -Value $l }
function CountFiles($p) { if (-not (Test-Path $p)) { return 0 }; return (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count }
$sevenZ = @("C:\Program Files\7-Zip\7z.exe", "C:\Program Files (x86)\7-Zip\7z.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
Get-ChildItem $base -Filter *.rar -File | ForEach-Object {
  $dest = Join-Path $base ([IO.Path]::GetFileNameWithoutExtension($_.Name))
  New-Item -ItemType Directory -Force -Path $dest | Out-Null
  Log "Extracting $($_.Name) ..."
  & tar.exe -xf $_.FullName -C $dest 2>&1 | ForEach-Object { Log "  $_" }
  $ok = ($LASTEXITCODE -eq 0 -and (CountFiles $dest) -gt 5)
  if (-not $ok -and $sevenZ) { & $sevenZ x $_.FullName "-o$dest" -y | Out-Null; $ok = ((CountFiles $dest) -gt 5) }
  if ($ok) { Remove-Item $_.FullName -Force; Log "  done: $(CountFiles $dest) files, rar deleted" }
  else { Log "  FAILED. Install 7-Zip (7-zip.org), then run this script again. The rar was kept." }
}
Get-ChildItem $base -Recurse -Directory | ForEach-Object {
  $c = (Get-ChildItem $_.FullName -File).Count; if ($c -gt 0) { Log ("   {0}  {1}" -f $_.FullName, $c) } }
Log "=== chilli done ==="
