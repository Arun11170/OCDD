# OCDD project setup. Safe to re-run: it never deletes or overwrites anything.
# Run in PowerShell:
#   powershell -ExecutionPolicy Bypass -File D:\OCDD\setup_ocdd.ps1
# Optional (large download, 54k images):
#   powershell -ExecutionPolicy Bypass -File D:\OCDD\setup_ocdd.ps1 -IncludePlantVillage

param([switch]$IncludePlantVillage)

$ErrorActionPreference = "Continue"
$Root = "D:\OCDD"

if (-not (Test-Path "D:\")) { Write-Host "D: drive not found. Stopping."; exit 1 }

# ---------- folders ----------
$dirs = @(
  "models\llama", "data\raw", "data\interim", "data\field_photos",
  "repos", "kb", "training", "logs", "benchmarks", "docs"
)
foreach ($d in $dirs) {
  New-Item -ItemType Directory -Force -Path (Join-Path $Root $d) | Out-Null
}

$log = Join-Path $Root "logs\setup_log.txt"
function Log($m) {
  $line = "{0}  {1}" -f (Get-Date -Format s), $m
  Write-Host $line
  Add-Content -Path $log -Value $line
}
Log "=== setup started ==="

# ---------- disk space ----------
foreach ($drv in @("C", "D")) {
  $p = Get-PSDrive -Name $drv -ErrorAction SilentlyContinue
  if ($p) { Log ("Drive {0}: free {1} GB" -f $drv, [math]::Round($p.Free / 1GB, 1)) }
}

# ---------- Llama files: find in Downloads/Desktop and move into models\llama ----------
$want = @("Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte", "tokenizer.model", "params.json")
$searchIn = @("$env:USERPROFILE\Downloads", "$env:USERPROFILE\Desktop", $Root, (Join-Path $Root "llama"))
foreach ($f in $want) {
  $dest = Join-Path $Root "models\llama\$f"
  if (Test-Path $dest) { Log "Llama file already in place: $f"; continue }
  $found = $null
  foreach ($s in $searchIn) {
    if (-not (Test-Path $s)) { continue }
    $found = Get-ChildItem -Path $s -Filter $f -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { break }
  }
  if ($found) {
    Move-Item -Path $found.FullName -Destination $dest
    Log "Moved $f from $($found.DirectoryName)"
  } else {
    Log "NOT FOUND: $f  (download it again from the Hugging Face repo page)"
  }
}
$pte = Join-Path $Root "models\llama\Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte"
if (Test-Path $pte) {
  $sz = (Get-Item $pte).Length
  if ($sz -gt 1100000000) { Log ("PTE size OK: {0:N0} bytes" -f $sz) }
  else { Log ("WARNING: PTE looks too small ({0:N0} bytes). The download was probably cut off. Download it again." -f $sz) }
}

# ---------- tool checks ----------
$tools = @(
  @("git", "--version"),
  @("python", "--version"),
  @("adb", "version"),
  @("java", "-version")
)
foreach ($t in $tools) {
  $c = Get-Command $t[0] -ErrorAction SilentlyContinue
  if ($c) {
    $out = & $t[0] $t[1] 2>&1 | Select-Object -First 1
    Log "FOUND   $($t[0]): $out"
  } else {
    Log "MISSING $($t[0])"
  }
}
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  $sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
  if (Test-Path $sdkAdb) { Log "adb exists at $sdkAdb but is not on PATH. Add that folder to PATH." }
}
if (Get-Command wsl -ErrorAction SilentlyContinue) { Log "FOUND   wsl (run 'wsl -l -v' to see which distros are installed)" }
else { Log "MISSING wsl" }
$studio = "C:\Program Files\Android\Android Studio\bin\studio64.exe"
if (Test-Path $studio) { Log "FOUND   Android Studio" } else { Log "MISSING Android Studio (or installed in a different folder)" }

# ---------- clone ExecuTorch demo app ----------
$git = Get-Command git -ErrorAction SilentlyContinue
$demo = Join-Path $Root "repos\executorch-examples"
$homeDemo = Join-Path $env:USERPROFILE "executorch-examples"
if (Test-Path $demo) {
  Log "Demo repo already cloned"
} elseif (Test-Path $homeDemo) {
  Log "Demo repo already exists at $homeDemo - not cloning again. Use that copy in Android Studio."
} elseif ($git) {
  Log "Cloning executorch-examples ..."
  git clone --depth 1 https://github.com/meta-pytorch/executorch-examples.git $demo 2>&1 | ForEach-Object { Log $_ }
} else {
  Log "Skipping clone: git is missing"
}
foreach ($d in @($demo, $homeDemo)) {
  if (Test-Path (Join-Path $d "llm\android\LlamaDemo")) { Log "Demo app folder OK: $d\llm\android\LlamaDemo" }
}

# ---------- datasets reachable by git ----------
if ($git) {
  $pd = Join-Path $Root "data\raw\PlantDoc-Dataset"
  if (-not (Test-Path $pd)) {
    Log "Cloning PlantDoc ..."
    git clone --depth 1 https://github.com/pratikkayal/PlantDoc-Dataset.git $pd 2>&1 | ForEach-Object { Log $_ }
  } else { Log "PlantDoc already cloned" }

  if ($IncludePlantVillage) {
    $pv = Join-Path $Root "data\raw\PlantVillage-Dataset"
    if (-not (Test-Path $pv)) {
      Log "Cloning PlantVillage (large, be patient) ..."
      git clone --depth 1 https://github.com/spMohanty/PlantVillage-Dataset.git $pv 2>&1 | ForEach-Object { Log $_ }
    } else { Log "PlantVillage already cloned" }
  } else {
    Log "PlantVillage skipped (large). Re-run with -IncludePlantVillage to clone it."
  }
}

# ---------- dataset list for the ones that need a browser download ----------
$ds = @'
# Datasets to download by hand (Mendeley needs the browser)

Save each into D:\OCDD\data\raw\<crop>\ and unzip. Keep the ORIGINAL images; split before augmenting.

| Crop | Link | License (as I saw it) |
|---|---|---|
| Groundnut | https://data.mendeley.com/datasets/22p2vcbxfk/3 | CC BY 4.0 |
| Chilli (COLD, with onion) | https://doi.org/10.17632/tf9dtfz9m6.3 | CC BY-NC |
| Okra | https://data.mendeley.com/datasets/nh7zk4hv8z/1 | CC BY-NC 4.0 |
| Rice (stretch) | https://data.mendeley.com/datasets/vwv3nry3wr/1 | not stated |
| Tomato | inside PlantVillage (cloned with -IncludePlantVillage) and PlantDoc (cloned) | check at M1 |

Mendeley sometimes offers both "original" and "augmented" folders. Take the original/raw images.
'@
Set-Content -Path (Join-Path $Root "docs\DATASETS.md") -Value $ds -Encoding ASCII
Log "Wrote docs\DATASETS.md"

Log "=== setup finished. Copy the whole contents of logs\setup_log.txt back to Claude. ==="
