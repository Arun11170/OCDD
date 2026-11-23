# Field Assistant: offline crop disease and pest advisor

**Built with Llama.** (Llama 3.2 1B Instruct, Meta; used under the Llama 3.2 Community License.)

An Android app (Kotlin, Jetpack Compose) that works with **no internet**. A farmer or extension worker photographs a leaf, fruit or pest; the phone names the likely problem, measures how bad it is, and writes practical advice in a local language.

## How it works (two separate pipelines)
1. **Vision pipeline**: photo -> EfficientNet-V2-S (ONNX Runtime) -> crop, problem, confidence, top-3 -> leaf/lesion segmentation + OpenCV -> damage % and severity band. It also rejects photos that are not a plant, blurry or too wide, and says "not sure" below a calibrated confidence gate.
2. **Advisory pipeline**: findings + a **verified knowledge base** (official Indian sources) -> **Llama 3.2 1B Instruct, run with ExecuTorch on the Arm CPU** (own Android process) -> output checker -> advisory in English or Hindi. If Llama is unavailable or its answer fails the check (invented chemical, dose, number, organism), the verified text is shown instead.

Other languages (Tamil, Telugu, Kannada, Malayalam, Bengali, Marathi, Gujarati, Punjabi, Odia, Assamese, Urdu) show the verified translated advice; Llama 3.2 officially supports only Hindi among them.

## Uncertainty and safety
Confident / unsure (call an extension worker) / cannot judge (blurry, wide, not a plant). Severe damage always adds the Kisan Call Centre / extension-worker prompt. Every result is a likely cause, not a confirmed diagnosis. Dose questions are never answered.

## Layout
- `app/CropAdvisor/`: the Android app (Kotlin, Jetpack Compose).
- `ml/`: training, export and evaluation: `train.py`, `export_onnx.py`, `get_negatives.py`, `accuracy_lab.py`, `benchmark.py`, data preparation, and the leaf and lesion segmentation trainers.
- `scripts/`: Windows launchers (`run_v7.bat` trains, `apply_run7.bat` installs the model, `build_apk.bat` builds the APK, `make_video.bat` converts the demo video).
- `tools/`: `summarize_metrics.py` (phone measurements to report tables), `make_gates.py` (per-crop confidence gates), voice tests.
- `kb_src/`, `kb/`: verified knowledge base and the scripts that build it. `pi5/`: Raspberry Pi 5 variant.
- `results/`: evidence reports (held-out test, independent 1,500-photo check, training reports).
- `docs/`: reproduction steps, offline audit, data licences, third-party notices, limits statement.
- `submission/`: final report (PDF) and demo video script.

## Run / reproduce
See `docs/REPRODUCE.md`. Offline evidence: `docs/OFFLINE_AUDIT.md`. Measurements: Settings > Run benchmark, then `python tools/summarize_metrics.py metrics.csv`.

## Licences
Llama 3.2 under the Llama 3.2 Community Licence; datasets and other models: `docs/DATA_LICENSES.md`, `docs/THIRD_PARTY_NOTICES.md`.
