# OCDD dataset downloader.
# For each dataset: download zip -> extract into D:\OCDD\data\raw\<name> -> delete the zip.
# Safe to re-run: finished datasets are skipped, partial downloads resume. No size limits.
# Okra is last (its Download All is about 8 GB). Press Ctrl+C when it starts if you want to skip it.
# Run:  powershell -ExecutionPolicy Bypass -File D:\OCDD\get_all_datasets.ps1
# Add -SkipHuggingFace to skip the tomato PlantVillage download from Hugging Face.

param([switch]$SkipHuggingFace)

$ErrorActionPreference = "Continue"
$Root = "D:\OCDD"
$dl = Join-Path $Root "data\downloads"
$rawRoot = Join-Path $Root "data\raw"
foreach ($d in @($dl, $rawRoot, (Join-Path $Root "logs"))) {
  New-Item -ItemType Directory -Force -Path $d | Out-Null
}
$log = Join-Path $Root "logs\datasets_log.txt"
function Log($m) {
  $line = "{0}  {1}" -f (Get-Date -Format s), $m
  Write-Host $line
  Add-Content -Path $log -Value $line
}
$results = @()

# Mendeley "Download All" link pattern: /public-api/zip/<dataset id>/download/<version>
$sets = @(
  @{ name = "groundnut";          url = "https://data.mendeley.com/public-api/zip/22p2vcbxfk/download/3" },
  @{ name = "chilli";             url = "https://data.mendeley.com/public-api/zip/tf9dtfz9m6/download/3" },
    @{ name = "rice";               url = "https://data.mendeley.com/public-api/zip/vwv3nry3wr/download/1" },
  @{ name = "rice_small";         url = "https://data.mendeley.com/public-api/zip/z4hx2v3ywc/download/1" },
  @{ name = "tomato_gtld";        url = "https://data.mendeley.com/public-api/zip/2bdfjb99k5/download/1" },
  @{ name = "tomato_maharashtra"; url = "https://data.mendeley.com/public-api/zip/rv3kxfv47y/download/1" },
  @{ name = "PlantDoc";           url = "https://github.com/pratikkayal/PlantDoc-Dataset/archive/HEAD.zip" },
  @{ name = "okra";               url = "https://data.mendeley.com/public-api/zip/nh7zk4hv8z/download/1" }
)

function IsZip($p) {
  if (-not (Test-Path $p)) { return $false }
  if ((Get-Item $p).Length -lt 100000) { return $false }
  $fs = [System.IO.File]::OpenRead($p)
  $b = New-Object byte[] 2
  [void]$fs.Read($b, 0, 2)
  $fs.Close()
  return ($b[0] -eq 0x50 -and $b[1] -eq 0x4B)
}

function CountFiles($p) {
  if (-not (Test-Path $p)) { return 0 }
  return (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count
}

Log "=== dataset download started ==="
foreach ($s in $sets) {
  $name = $s.name
  $out = Join-Path $rawRoot $name

  if ((CountFiles $out) -gt 10) {
    Log "SKIP $name : already extracted"
    $results += "$name : already done"
    continue
  }

  # 1) a zip already in the downloads folder (manual download or earlier run)?
  $zip = $null
  $found = Get-ChildItem $dl -Filter "$name*.zip" -File -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($found -and (IsZip $found.FullName)) {
    $zip = $found.FullName
    Log "Using existing zip $zip"
  }

  # 2) otherwise download it
  if (-not $zip) {
    $zip = Join-Path $dl "$name.zip"
    Log "Downloading $name ..."
    & curl.exe -L --retry 5 --retry-delay 5 -C - -o $zip $s.url
    if (-not (IsZip $zip)) {
      Log "FAILED $name : the download did not produce a zip. Download it in the browser and save it in $dl as $name.zip"
      $results += "$name : download failed"
      continue
    }
    Log ("Downloaded {0:N0} MB" -f ((Get-Item $zip).Length / 1MB))
  }

  # 3) extract
  New-Item -ItemType Directory -Force -Path $out | Out-Null
  $ok = $false
  & tar.exe -xf $zip -C $out 2>&1 | ForEach-Object { Log $_ }
  if ($LASTEXITCODE -eq 0 -and (CountFiles $out) -gt 0) { $ok = $true }
  if (-not $ok) {
    try {
      Expand-Archive -Path $zip -DestinationPath $out -Force -ErrorAction Stop
      if ((CountFiles $out) -gt 0) { $ok = $true }
    } catch {
      Log "extract error: $($_.Exception.Message)"
    }
  }
  if (-not $ok) {
    Log "FAILED to extract $name. The zip was kept at $zip. Delete it and re-run if it is incomplete."
    $results += "$name : extract failed"
    continue
  }

  # 4) delete the zip
  Remove-Item $zip -Force
  Log "Extracted $name and deleted the zip"
  $results += "$name : done ($(CountFiles $out) files)"

  Get-ChildItem $out -Recurse -Directory | ForEach-Object {
    $c = (Get-ChildItem $_.FullName -File).Count
    if ($c -gt 0) { Log ("   {0}  {1}" -f $_.FullName, $c) }
  }
}

# ---------- tomato PlantVillage from Hugging Face (parquet files, no zip) ----------
if (-not $SkipHuggingFace) {
  $pvOut = Join-Path $rawRoot "tomato_plantvillage"
  if ((CountFiles $pvOut) -gt 2) {
    Log "SKIP tomato_plantvillage : already downloaded"
    $results += "tomato_plantvillage : already done"
  } else {
    Log "Downloading PlantVillage from Hugging Face (about 850 MB) ..."
    & python -m pip install --quiet huggingface_hub 2>&1 | ForEach-Object { Log $_ }
    $py = Join-Path $env:TEMP "ocdd_hf.py"
    @"
from huggingface_hub import snapshot_download
snapshot_download(repo_id="BrandonFors/Plant-Diseases-PlantVillage-Dataset", repo_type="dataset", local_dir=r"$pvOut")
print("hf download finished")
"@ | Set-Content -Path $py -Encoding ASCII
    & python $py 2>&1 | ForEach-Object { Log $_ }
    if ((CountFiles $pvOut) -gt 2) {
      $results += "tomato_plantvillage : done ($(CountFiles $pvOut) files)"
    } else {
      $results += "tomato_plantvillage : failed"
    }
  }
}

Log "=== SUMMARY ==="
foreach ($r in $results) { Log $r }
Log "=== finished. Paste logs\datasets_log.txt to Claude. ==="
