# Adds the voice-language model files to the existing public release "model-v1" and replaces manifest.json with the updated one.
# First run:  python prepare_voice_ai.py      Then, from D:\OCDD:   .\publish_voice_ai.ps1
$ErrorActionPreference = "Stop"
$files = @("D:\OCDD\models\voice_ai\embed.onnx", "D:\OCDD\models\voice_ai\embed_vocab.tsv", "D:\OCDD\models\llama\manifest.json")
foreach ($f in $files) { if (-not (Test-Path $f)) { throw "missing $f  (run prepare_voice_ai.py first)" } }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI not found. In the browser instead:" -ForegroundColor Yellow
    Write-Host "  Open https://github.com/Arun11170/OCDD/releases/tag/model-v1 -> Edit -> drag in these 3 files (replace manifest.json) -> Update release"
    $files | ForEach-Object { Write-Host "       $_" }
    exit 0
}
gh release upload model-v1 $files --repo Arun11170/OCDD --clobber
Write-Host "Done. Check: https://github.com/Arun11170/OCDD/releases/download/model-v1/manifest.json" -ForegroundColor Green
