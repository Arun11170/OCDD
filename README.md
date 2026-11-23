# Field Assistant: offline crop disease and pest advisor

Team Dexters (Pravin Arun R J, Nithishwaran S, Muhil T A) | Bharat AI-SoC Student Challenge, Field Assistant problem statement

**Built with Llama.** (Llama 3.2 1B Instruct, Meta; used under the Llama 3.2 Community License.)

An Android app (Kotlin, Jetpack Compose) that works with **no internet**. A farmer or extension worker photographs a leaf, fruit or pest; the phone names the likely problem, measures how bad it is, and writes practical advice in a local language.

## How it works (two separate pipelines)
1. **Vision pipeline**: photo -> EfficientNet-V2-S (ONNX Runtime) -> crop, problem, confidence, top-3 -> leaf/lesion segmentation + OpenCV -> damage % and severity band. It also rejects photos that are not a plant, blurry or too wide, and says "not sure" below a calibrated confidence gate.
2. **Advisory pipeline**: findings + a **verified knowledge base** (official Indian sources) -> **Llama 3.2 1B Instruct, run with ExecuTorch on the Arm CPU** (own Android process) -> output checker -> advisory in English or Hindi. If Llama is unavailable or its answer fails the check (invented chemical, dose, number, organism), the verified text is shown instead.

Other languages (Tamil, Telugu, Kannada, Malayalam, Bengali, Marathi, Gujarati, Punjabi, Odia, Assamese, Urdu) show the verified translated advice; Llama 3.2 officially supports only Hindi among them.

## Uncertainty and safety
Confident / unsure (call an extension worker) / cannot judge (blurry, wide, not a plant). Severe damage always adds the Kisan Call Centre / extension-worker prompt. Every result is a likely cause, not a confirmed diagnosis. Dose questions are never answered.

## Layout
- `app/CropAdvisor/`: the Android app (Kotlin, Jetpack Compose) with the bundled models and knowledge base.
- `ml/`: training, export and evaluation code (`train.py`, `export_onnx.py`, `get_negatives.py`, `accuracy_lab.py`, data preparation, segmentation trainers, `make_gates.py`, `summarize_metrics.py`).
- `results/`: held-out test report and the independent 1,500-photo check for the final model.
- `docs/`: reproduction steps, offline audit, data sources and licences, third-party notices, limits statement.
- `submission/`: the final project report (PDF).

## Results (final model, held-out photos)
| Measure | Result |
|---|---|
| Test accuracy / macro-F1 (15,561 photos) | 91.8% / 0.897 |
| Field photos / lab photos | 88.0% / 99.8% |
| Answers at gate 0.80 / correct when it answers | 93.3% / 95.3% |
| Independent 1,500-photo check (top-1, field) | 93.0% (89.2%) |
| Non-plant photos recognised | 97.8% |
| Calibration error (ECE) | 0.021 |

Full details, charts and the on-device method are in `submission/Field_Assistant_Final_Report.pdf`; raw numbers are in `results/`.

## Run the app
Install the APK from the repository's Releases page on an Arm Android phone (about 6 GB RAM recommended). Open it once with internet to download the Llama model (1.2 GB); after that it works fully offline. Build from source by opening `app/CropAdvisor` in Android Studio (release build).

## Reproduce training
See `docs/REPRODUCE.md`. Offline evidence: `docs/OFFLINE_AUDIT.md`. Measurements: Settings > Run benchmark, then `python ml/summarize_metrics.py metrics.csv`.

## Licences
Llama 3.2 under the Llama 3.2 Community Licence; datasets and other models: `docs/DATA_LICENSES.md`, `docs/THIRD_PARTY_NOTICES.md`.
