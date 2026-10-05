# Finds the chilli and okra zips you downloaded by hand, extracts them to data\raw\<name>, deletes the zips.
# Run:  powershell -ExecutionPolicy Bypass -File D:\OCDD\place_manual.ps1
$ErrorActionPreference = "Continue"
$Root = "D:\OCDD"; $raw = Join-Path $Root "data\raw"
$dl = Join-Path $Root "data\downloads"
$log = Join-Path $Root "logs\datasets_log.txt"
function Log($m) { $l = "{0}  {1}" -f (Get-Date -Format s), $m; Write-Host $l; Add-Content -Path $log -Value $l }
function CountFiles($p) { if (-not (Test-Path $p)) { return 0 }; return (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count }
function IsZip($p) {
  $fs = [System.IO.File]::OpenRead($p); $b = New-Object byte[] 2; [void]$fs.Read($b,0,2); $fs.Close()
  return ($b[0] -eq 0x50 -and $b[1] -eq 0x4B)
}
function Place($name, $pattern, [bool]$clean) {
  $out = Join-Path $raw $name
  $cands = @()
  foreach ($d in @("$env:USERPROFILE\Downloads", $dl)) {
    if (Test-Path $d) { $cands += Get-ChildItem $d -Filter $pattern -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -eq ".zip" -and $_.Length -gt 1MB } }
  }
  $cands = @($cands | Sort-Object LastWriteTime -Descending)
  if ($cands.Count -eq 0) { Log "$name : no zip found in Downloads or $dl"; return }
  foreach ($c in $cands) {
    Log ("$name : trying {0} ({1:N0} MB)" -f $c.FullName, ($c.Length / 1MB))
    if (-not (IsZip $c.FullName)) { Log "  not a valid zip, skipping"; continue }
    if ($clean -and (Test-Path $out)) { Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue }
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    & tar.exe -xf $c.FullName -C $out 2>&1 | ForEach-Object { Log "  $_" }
    if ($LASTEXITCODE -eq 0 -and (CountFiles $out) -gt 5) {
      Remove-Item $c.FullName -Force
      Log "$name : extracted, zip deleted ($(CountFiles $out) files)"
      # nested archives
      Get-ChildItem $out -Recurse -File -Include *.zip,*.7z,*.rar -ErrorAction SilentlyContinue | ForEach-Object {
        Log "  nested archive $($_.FullName)"
        & tar.exe -xf $_.FullName -C $out 2>&1 | ForEach-Object { Log "  $_" }
        if ($LASTEXITCODE -eq 0) { Remove-Item $_.FullName -Force; Log "  extracted and deleted" }
      }
      Log "$name : now $(CountFiles $out) files"
      return
    }
    Log "  extraction failed or incomplete - the download was probably cut off. Download it again."
  }
}
Log "=== place manual downloads ==="
Place "chilli" "*chil*" $false
Place "okra"   "*okra*" $true
foreach ($n in "chilli","okra") { Log ("{0} : {1} files" -f $n, (CountFiles (Join-Path $raw $n))) }
Get-ChildItem (Join-Path $raw "chilli"),(Join-Path $raw "okra") -Recurse -Directory -ErrorAction SilentlyContinue | ForEach-Object {
  $c = (Get-ChildItem $_.FullName -File).Count; if ($c -gt 0) { Log ("   {0}  {1}" -f $_.FullName, $c) } }
Log "=== done ==="
