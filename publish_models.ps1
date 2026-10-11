# Publishes the Llama files as the public GitHub release "models-v1" that the app's Smart download reads.
# Needs the GitHub CLI once:  winget install GitHub.cli   then   gh auth login
# Run from D:\OCDD:   .\publish_models.ps1
$ErrorActionPreference = "Stop"
$dir = "D:\OCDD\models\llama"
$files = @("$dir\manifest.json", "$dir\tokenizer.model", "$dir\Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte")
foreach ($f in $files) { if (-not (Test-Path $f)) { throw "missing $f" } }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI not found. Do it in the browser instead:" -ForegroundColor Yellow
    Write-Host "  1. Open https://github.com/Arun11170/OCDD/releases/new"
    Write-Host "  2. Tag: models-v1   Title: Llama model files   (tick 'Set as the latest release' off is fine)"
    Write-Host "  3. Drag these 3 files in, wait for the upload, then Publish release:"
    $files | ForEach-Object { Write-Host "       $_" }
    Start-Process "https://github.com/Arun11170/OCDD/releases/new"
    exit 0
}
gh release create models-v1 $files --repo Arun11170/OCDD --title "Llama model files" --notes "Llama 3.2 1B Instruct (QLoRA INT4) for the optional Smart answers. Built with Llama. Used under the Llama 3.2 Community License."
Write-Host "Done. Test: https://github.com/Arun11170/OCDD/releases/download/models-v1/manifest.json" -ForegroundColor Green
