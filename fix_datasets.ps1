# Fixes after the first dataset run: nested archives, chilli, okra.
# Run:  powershell -ExecutionPolicy Bypass -File D:\OCDD\fix_datasets.ps1
$ErrorActionPreference = "Continue"
$Root = "D:\OCDD"
$dl = Join-Path $Root "data\downloads"
$raw = Join-Path $Root "data\raw"
$log = Join-Path $Root "logs\datasets_log.txt"
function Log($m) { $l = "{0}  {1}" -f (Get-Date -Format s), $m; Write-Host $l; Add-Content -Path $log -Value $l }
function CountFiles($p) { if (-not (Test-Path $p)) { return 0 }; return (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count }
function IsZip($p) {
  if (-not (Test-Path $p)) { return $false }
  if ((Get-Item $p).Length -lt 100000) { return $false }
  $fs = [System.IO.File]::OpenRead($p); $b = New-Object byte[] 2; [void]$fs.Read($b,0,2); $fs.Close()
  return ($b[0] -eq 0x50 -and $b[1] -eq 0x4B)
}

# extract an archive that sits inside a dataset folder, then delete it
function Expand-Nested($archive, $dest) {
  if (-not (Test-Path $archive)) { Log "not found: $archive"; return }
  $before = CountFiles $dest
  Log "Extracting $archive ..."
  & tar.exe -xf $archive -C $dest 2>&1 | ForEach-Object { Log $_ }
  $ok = ($LASTEXITCODE -eq 0 -and (CountFiles $dest) -gt ($before + 1))
  if (-not $ok -and $archive -like "*.7z") {
    $z = "C:\Program Files\7-Zip\7z.exe"
    if (Test-Path $z) { & $z x $archive "-o$dest" -y | Out-Null }
    else {
      Log "tar could not read the 7z; trying py7zr ..."
      & python -m pip install --quiet py7zr 2>&1 | Out-Null
      & python -c "import py7zr,sys; py7zr.SevenZipFile(sys.argv[1]).extractall(sys.argv[2])" $archive $dest 2>&1 | ForEach-Object { Log $_ }
    }
    $ok = ((CountFiles $dest) -gt ($before + 1))
  }
  if ($ok) { Remove-Item $archive -Force; Log "Extracted and deleted $archive  (files now: $(CountFiles $dest))" }
  else { Log "FAILED to extract $archive (kept it)" }
}

Log "=== fix run started ==="
Expand-Nested (Join-Path $raw "rice\Rice Leaf Disease and Pest Dataset Overview\Rice Dataset.zip") (Join-Path $raw "rice")
Expand-Nested (Join-Path $raw "tomato_maharashtra\New folder\New folder\Real world data.7z") (Join-Path $raw "tomato_maharashtra")

# ---- chilli: the version-3 link gave 404, try other versions ----
$chOut = Join-Path $raw "chilli"
if ((CountFiles $chOut) -gt 10) { Log "SKIP chilli : already extracted" }
else {
  Remove-Item (Join-Path $dl "chilli.zip") -Force -ErrorAction SilentlyContinue
  foreach ($v in 3,2,1,4) {
    $zip = Join-Path $dl "chilli.zip"
    Log "chilli: trying version $v ..."
    & curl.exe -L --silent --show-error --retry 3 -o $zip "https://data.mendeley.com/public-api/zip/tf9dtfz9m6/download/$v"
    if (IsZip $zip) {
      New-Item -ItemType Directory -Force -Path $chOut | Out-Null
      & tar.exe -xf $zip -C $chOut 2>&1 | ForEach-Object { Log $_ }
      if ((CountFiles $chOut) -gt 0) { Remove-Item $zip -Force; Log "chilli extracted, zip deleted ($(CountFiles $chOut) files)"; break }
    }
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
  }
  if ((CountFiles $chOut) -eq 0) { Log "chilli FAILED on all versions. Download it in the browser into $dl as chilli.zip and run this script again." }
}

# ---- okra: list files through the Mendeley API, take the small 224 file(s) ----
$okOut = Join-Path $raw "okra"
if ((CountFiles $okOut) -gt 10) { Log "SKIP okra : already extracted" }
else {
  Remove-Item (Join-Path $dl "okra.zip") -Force -ErrorAction SilentlyContinue   # the old one was cut off (21 MB)
  try {
    $r = Invoke-RestMethod -Uri "https://data.mendeley.com/public-api/datasets/nh7zk4hv8z/files?folder_id=root&version=1" -UseBasicParsing
    foreach ($f in $r) { Log ("okra file: {0}  {1:N0} MB" -f $f.filename, ($f.size / 1MB)) }
    $pick = @($r | Where-Object { $_.filename -match "224" })
    if ($pick.Count -eq 0) { $pick = @($r | Sort-Object size | Select-Object -First 1) }
    New-Item -ItemType Directory -Force -Path $okOut | Out-Null
    foreach ($f in $pick) {
      $dst = Join-Path $dl $f.filename
      Log "okra: downloading $($f.filename) ..."
      & curl.exe -L --retry 5 --retry-delay 5 -C - -o $dst $f.content_details.download_url
      if ($dst -like "*.zip" -and (IsZip $dst)) {
        & tar.exe -xf $dst -C $okOut 2>&1 | ForEach-Object { Log $_ }
        if ((CountFiles $okOut) -gt 0) { Remove-Item $dst -Force; Log "okra extracted, zip deleted ($(CountFiles $okOut) files)" }
      } else { Move-Item $dst $okOut -Force; Expand-Nested (Join-Path $okOut $f.filename) $okOut }
    }
  } catch {
    Log "okra API failed: $($_.Exception.Message). Open https://data.mendeley.com/datasets/nh7zk4hv8z/1 , Files tab, and send Claude a screenshot."
  }
}

foreach ($n in "rice","rice_small","tomato_maharashtra","tomato_gtld","chilli","okra","PlantDoc","tomato_plantvillage","groundnut") {
  Log ("{0} : {1} files" -f $n, (CountFiles (Join-Path $raw $n)))
}
Log "=== fix run finished. Paste logs\datasets_log.txt (last part) to Claude. ==="
