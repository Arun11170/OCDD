---
title: "Field Assistant: Offline Crop Disease and Pest Detection with an On-Device Llama Advisor"
subtitle: "Bharat AI-SoC Student Challenge, Final Project Report"
author: "Team OCDD (Arun) | Repository: https://github.com/Arun11170/OCDD"
date: "November 2026"
---

# 1. Summary

Field Assistant is an Android app (Kotlin, Jetpack Compose) that works with no internet. A farmer or extension worker photographs a leaf, fruit or insect. On the phone alone, the app (1) names the likely disease or pest with a calibrated confidence, (2) measures how much of the leaf is damaged, and (3) writes practical advice in the farmer's language. When the photo is unclear, the model is unsure, or the damage is severe, the app says so and sends the farmer to an extension worker or the Kisan Call Centre (1800-180-1551) instead of guessing.

The system uses **two separate pipelines**: a vision pipeline (ONNX Runtime on the Arm CPU) and an advisory pipeline in which **Llama 3.2 1B Instruct runs through ExecuTorch on the Arm CPU**. No Hailo or other accelerator is used. The offline claim was audited: only the one-time model download touches the network (docs/OFFLINE_AUDIT.md).

# 2. Problem and requirements

Smallholder farmers often have no signal in the field and no expert nearby. The problem statement asks for: a vision model for disease/pest, confidence and severity; a Llama 3.2 1B/3B advisor deployed with ExecuTorch on Arm; local-language output; uncertainty handling (blurry photo, low confidence, severe damage); a fully offline demo; and measured latency, memory and power. Each requirement and where it is met:

| Requirement | How it is met |
|---|---|
| Disease/pest + confidence + severity | EfficientNet-V2-S classifier, temperature-scaled; leaf/lesion segmentation + OpenCV gives damage % and a 4-level severity |
| Llama 3.2 1B/3B via ExecuTorch on Arm | Llama 3.2 1B Instruct QLoRA INT4 (.pte, 1.18 GB), `org.pytorch:executorch-android:1.5.1`, `LlmModule`, own Android process |
| Local languages | 13 languages in the UI and verified advice; Llama writes English and Hindi; other languages enabled for Llama only if they pass the on-device benchmark |
| Uncertainty handling | Quality checks (blur, too wide), per-crop confidence gates, "not a plant" class, severity prompt, extension-worker prompt |
| Fully offline | Models stored on the phone; offline audit; demo recorded in airplane mode |

# 3. Methodology

## 3.1 Architecture

```
photo -> quality check -> EfficientNet-V2-S (ONNX Runtime, CPU, 224 px)
      -> temperature scaling -> per-crop gate -> crop, problem, confidence, top-3
      -> leaf seg + lesion seg + OpenCV -> damage % and severity band
                          |
                          v   findings + region + verified KB entry
          prompt -> Llama 3.2 1B (ExecuTorch, own process) -> output checker
                          -> advisory text (or verified text as fallback)
```

Why two pipelines: recognising an image and writing text are different tasks. The vision model can be retrained without touching the advisor, each is tested alone, and a low-memory kill of the Llama process never takes down the detector.

## 3.2 Vision pipeline

- **Model:** ImageNet-pretrained EfficientNet-V2-S, 224 x 224, 95 plant/pest classes plus a "not a plant" class (96). Exported to ONNX, run with ONNX Runtime on the CPU, fp32 (81 MB).
- **Data:** PlantDoc, PlantVillage, Mendeley sets (groundnut, chilli, okra, rice, tomato), IP102 (pests), and Kaggle sets for mango, sugarcane, maize and banana: 14 crops plus pests. Splits are made by photo group to avoid leakage. Field photos and lab photos are reported separately because lab photos are easy (0.997) and overstate real use. The "not a plant" class uses COCO val2017 scenes, DTD textures, and degraded copies of real leaves (blurry, dark, tiny in a large scene, blown out).
- **Training:** 20 epochs, batch 32, lr 5e-4, rare-class sampling boost (power 0.75), mixed precision on an RTX 3050 6 GB laptop GPU.
- **Calibration and gates:** temperature scaling gives well-calibrated confidence (ECE 0.023). The app answers only if confidence is above a gate (base 0.80; stricter for chilli 0.90 and pests 0.85, the weak groups). Below the gate it says "not sure" and shows the top choices.
- **Severity:** leaf and lesion segmentation plus OpenCV measure the affected area; the percentage maps to very light / mild / moderate / severe.

## 3.3 Advisory pipeline (Llama 3.2 1B on ExecuTorch)

- **Deployment:** Llama 3.2 1B Instruct, QLoRA INT4 with 8-bit embeddings (`Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte`, 1.18 GB), run with the ExecuTorch Android runtime (`LlmModule`) on the Arm CPU. It runs in a separate Android process (`:llm`) so memory is returned at once when unloaded and a crash cannot take down the app.
- **Grounding:** Llama sees only the vision findings, the measured damage, the region, and the verified facts for that problem (cause, symptoms, field steps, when to act, treatments named by official Indian sources: TNAU, ICAR, state universities). Temperature 0.2.
- **Safety:** dose and mixing-rate questions are never sent to Llama; the app refers to the product label and the Kisan Call Centre. Every generated answer passes an output checker (numbers, chemicals, organisms and disease names not in the facts; length; correct script and vocabulary for non-English). If it fails, the verified text is shown. The verified text is already on screen while Llama writes, so the farmer never waits on a blank screen.
- **Languages:** English and Hindi are generated by Llama (Hindi is the only Indian language Llama 3.2 officially supports; it has an extra chemical blocklist). Other languages use Llama only if the per-language on-device benchmark shows at least 80% of answers pass the checker; otherwise the verified, machine-translated advice is shown.

# 4. Optimization techniques

| Technique | Effect |
|---|---|
| Llama 3.2 1B with QLoRA INT4 weights, 8-bit embeddings | 1.18 GB model that fits on mid-range phones |
| ExecuTorch runtime on Arm CPU, own process (`:llm`) | measured about 1.9 GB peak PSS at load; unload frees it at once; isolates crashes |
| Memory gate (needs about 1.8 GB free) | weaker phones skip Llama and show verified text instead of crashing |
| Vision stays fp32; int8 tried and rejected | int8 lost far too much accuracy; fp32 is only 81 MB |
| Temperature scaling + per-crop gates | confidence that can be trusted; fewer wrong answers on weak groups |
| Verified text first, Llama swaps in only if checked | no blank wait; no unchecked output shown |
| Resized 256 px training cache, 3 workers | made training possible on a 6 GB GPU laptop |
| Lazy model load / unload | Llama is loaded only when an advisory is requested |

# 5. Vision results

Held-out test set, shipped model (run 5, 14,262 photos): **accuracy 0.911, macro-F1 0.889**. Field photos 0.863 (n = 9,188); lab photos 0.997 (n = 5,074). Calibration error 0.023.

| Gate | Photos answered | Correct when answered |
|---|---|---|
| 0.80 | 92.3% | 94.9% |
| 0.90 | 88.1% | 96.4% |

Independent check on 1,500 held-out photos with the shipped file: top-1 0.921 (field 0.878); at gate 0.80 it answers 93.1% and is right 95.1%. Healthy leaf called affected 3.4%; affected called healthy 1.2%. Weakest groups: pests (right 90.5% when answering) and chilli; most-swapped pairs are look-alike pests (leafhopper/planthopper, rice leaf folder/stem borer).

The "not a plant" class (run 7, 96 classes, 107,636 images) is added to stop the app naming a disease for a hand, a wall or a blurry scene. If the belief on those classes is 0.40 or more, the app shows "No leaf or pest found" and asks for a retake.

# 6. Hardware utilization and on-device performance

**Compute:** both pipelines run on the Arm CPU of the phone (ONNX Runtime for vision, ExecuTorch for Llama). No GPU, NPU or Hailo accelerator is used. **Storage:** vision model 81 MB, Llama .pte 1.18 GB, plus segmentation models and knowledge base. **Memory:** Llama process about 1.9 GB PSS measured at load; the app process holds the vision model and UI.

Latency, tokens per second, memory and battery are measured on the phone by Settings > Run benchmark (39 advisories, 3 per language) and summarised with `tools/summarize_metrics.py`; energy is estimated from the battery-percentage drop and labelled as an estimate.

DEVICE_RESULTS_PLACEHOLDER

# 7. Usability and reliability

Offline behaviour was verified with the network off. The app was tested on the target phone for the main flows (photo, result, advisory, language switch, voice commands, unclear photo, not-a-plant photo). Voice input and read-aloud work in the supported languages; speech stops when a new check starts and reads only the problem and the fix. Reliability design: every failure path falls back to verified text; the Llama process can be killed without losing the result; a self-check screen confirms all models are on the device.

USABILITY_PLACEHOLDER

# 8. Limitations

- Field accuracy (0.86 to 0.88) is below lab accuracy (0.997). The app compensates by saying "not sure" instead of guessing, at the cost of coverage. Real-farm accuracy will probably be lower than the dataset numbers.
- Pests are the weakest group; look-alike pests are hard to separate from a photo.
- A 1B model can be vague or wrong. The checker lowers but cannot remove the risk, so the verified text is the fallback and every result says "likely cause, not a confirmed diagnosis".
- Llama needs about 2 GB of free RAM; weaker phones get the verified text only.
- Some datasets are non-commercial (CC BY-NC); this restricts commercial use of the trained model (docs/DATA_LICENSES.md).
- Translations for the 11 non-Hindi languages are machine-made and not all reviewed by native speakers.
- Advice names products but never doses; label claims per crop were not checked.

# 9. Reproducibility

Source code, training scripts (`train.py`, `export_onnx.py`, `get_negatives.py`, `accuracy_lab.py`), the knowledge base, the Android project and test tools are in the repository. Steps: README.md and docs/REPRODUCE.md. The trained models are on the public GitHub release `model-v1`; the app downloads them once and then runs in airplane mode.

# 10. Credits and licences

Built with Llama (Llama 3.2 Community License, Meta). Datasets, models and papers: docs/DATA_LICENSES.md and docs/THIRD_PARTY_NOTICES.md.
