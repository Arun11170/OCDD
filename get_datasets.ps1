# Downloads and extracts the datasets into D:\OCDD\data\raw\<name>.
# Safe to re-run: it skips anything already extracted and resumes partial downloads.
# Run in PowerShell:
#   powershell -ExecutionPolicy Bypass -File D:\OCDD\get_datasets.ps1

$ErrorActionPreference = "Continue"
$Root = "D:\OCDD"
$dl = Join-Path $Root "data\downloads"
New-Item -ItemType Directory -Force -Path $dl | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Root "logs") | Out-Null
$log = Join-Path $Root "logs\datasets_log.txt"
function Log($m) {
  $line = "{0}  {1}" -f (Get-Date -Format s), $m
  Write-Host $line
  Add-Content -Path $log -Value $line
}

# Mendeley link pattern: /public-api/zip/<dataset id>/download/<version>  (untested from my side)
$sets = @(
  @{ name = "chilli";   url = "https://data.mendeley.com/public-api/zip/tf9dtfz9m6/download/3" },
  @{ name = "PlantDoc"; url = "https://github.com/pratikkayal/PlantDoc-Dataset/archive/HEAD.zip" }
  # okra (8 GB with Download All) and rice are left out on purpose: pick the small files by hand in the browser.
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

Log "=== dataset download started ==="
foreach ($s in $sets) {
  $zip = Join-Path $dl "$($s.name).zip"
  $out = Join-Path $Root "data\raw\$($s.name)"

  $existing = 0
  if (Test-Path $out) { $existing = (Get-ChildItem $out -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count }
  if ($existing -gt 10) { Log "SKIP $($s.name): already extracted ($existing files)"; continue }

  if (-not (IsZip $zip)) {
    Log "Downloading $($s.name) ..."
    & curl.exe -L --retry 5 --retry-delay 5 -C - -o $zip $s.url
  }
  if (-not (IsZip $zip)) {
    Log "FAILED $($s.name): the download did not produce a zip. Download it in the browser instead (see docs\DATASETS.md)."
    continue
  }

  New-Item -ItemType Directory -Force -Path $out | Out-Null
  try {
    Expand-Archive -Path $zip -DestinationPath $out -Force -ErrorAction Stop
    Log "Extracted $($s.name) to $out"
  } catch {
    Log "FAILED to extract $($s.name): $($_.Exception.Message). The zip may be incomplete: delete $zip and re-run."
    continue
  }

  Get-ChildItem $out -Recurse -Directory | ForEach-Object {
    $c = (Get-ChildItem $_.FullName -File).Count
    if ($c -gt 0) { Log ("{0}  {1}" -f $_.FullName, $c) }
  }
}
Log "=== finished. Paste logs\datasets_log.txt to Claude. ==="
