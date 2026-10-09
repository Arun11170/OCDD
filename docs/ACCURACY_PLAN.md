# Accuracy plan (10 Oct 2026)

Measured with `accuracy_lab.py` on 1500 held-out test photos (961 field, 539 lab), the same vision.onnx the app ships.
Report: `accuracy_lab_report.txt`. Re-run after every new model: `python3 accuracy_lab.py 1500` (resumable, ~5 photos/s).

## What the numbers say
| Idea | Result | Decision |
|---|---|---|
| Average flip / zoom views (test-time averaging) | top-1 unchanged (0.921 vs 0.921). Only makes the app say "not sure" more often | **Not added** |
| Crop picker (oracle crop) | top-1 +0.2 pt, field answers +1.1 pt. Model already separates crops well | Keep picker (it also filters outputs); not a big accuracy lever |
| Per-crop gate | chilli: 85% right at 0.80 -> 88.5% at 0.90; pests: 90.1% -> 91.8% at 0.85. Overall right-when-answers 95.1 -> 95.9 (field 92.0 -> 93.2), answers 93.1% -> 91.7% | **Added** (`assets/gates.json`, never below 0.80). Rice not raised (would drop its answers 78% -> 65%) |
| Confusion pairs | mostly pests: leafhopper/planthopper, rice leaf folder/stem borer/corn borer/army worm/cutworm; maize blight vs gray leaf spot; rice stripes vs leaf scald; okra phyllosticta vs downy mildew | Added to `Confusable.kt` (warning + points to "Check to be sure") |
| Calibration | ECE 0.021 already (temperature scaling is inside the model) | Nothing to do |

## What only data can fix (YOU / people)
1. **Rice (top-1 0.74), chilli (0.85), okra (0.88), pests (0.84)** are the weak groups. They need more real field photos, not a different trick.
2. **Field photo test set**: 200+ real photos labelled by a KVK, never used for training (the current "field" domain is still dataset photos).
3. **Hard-negative training**: retrain with extra examples of the swapped pairs above (`add_plantseg.py` / `train.py`).
4. **Field fine-tuning**: 50-100 farm photos per weak crop with real blur, shadow, soil, hands.
5. Use the app's "Was this right?" CSV and the Better/Same/Worse CSV from the pilot to pick the next pairs.

Caveat: all numbers are on dataset test photos. Real-field accuracy will be lower until item 2 exists.
