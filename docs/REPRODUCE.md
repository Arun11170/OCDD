# Reproducing Field Assistant

1. Clone the repository. Open `app/CropAdvisor` in Android Studio, build the `app` module, install on an Arm64 phone (ARMv8.1+ for ExecuTorch).
2. Llama files: download `Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte` and `tokenizer.model` (Meta, Llama 3.2 licence, via Hugging Face after accepting the licence) and push to `/data/local/tmp/llama/` (or let the in-app download fetch them from the release).
3. Vision model: bundled as `assets/vision.onnx`. To retrain: `python ml/get_data.py`, `python ml/prepare_data.py`, `python ml/get_negatives.py`, then `python ml/train.py --arch efficientnet_v2_s --interim interim_v4 --epochs 20 --batch 32 --lr 5e-4 --sampling-power 0.75 --out run7` and `python ml/export_onnx.py`.
4. Test offline: enable airplane mode, open the app, Settings > Self-check.
5. Measurements: use the app for 20+ scans, copy `metrics.csv` from `Android/data/com.cropadvisor/files/`, run `python ml/summarize_metrics.py`.
