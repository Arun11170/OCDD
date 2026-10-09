# Crop Advisor: implementation plan

Legend: [x] written (not yet run on a phone unless stated) · [T] tested by measurement · [ ] open · **YOU** = needs your computer or phone.
Nothing has been compiled by me since the first feature batch: the Android Studio build is the first real test of everything marked [x].
Last updated: 9 Oct 2026.

## 1. App features
- [x] Photo → disease/pest with confidence gate (ONNX Runtime, 95 classes)  [T: v3 run3 test top1 0.916]
- [x] Damage % with leaf finder + lesion finder, damage-only overlay  (needs trained models, see 3)
- [x] "Check to be sure": symptom tree over all problems of the crop (114 questions, EN+HI)  [T: simulation only]
- [x] Pest cause links, region prior, history, share, welcome page, licences, Built with Llama
- [x] Photo quality check, Was-this-right feedback + CSV export, follow-up comparison, spread rate per week
- [x] Organic / low-cost options (81 problems, sourced) and spray-safety card
- [x] Plot mode (several photos), voice question input
- [x] Crop picker with filtered outputs; per-crop specialist loader (one crop model in memory at a time)
- [x] Extra photo of the same plant (underside / pest) combined or shown as the cause
- [x] Llama build chosen by total RAM, free RAM now, free storage, CPU; photo models freed before Llama loads
- [x] First-run download of the Llama build (resume, SHA-256, storage check)
- [x] Village hub client for phones too small for Llama (answer still checked on the phone)
- [x] Spray-or-wait card, treatment re-check reminders (notification + Home banner), season diary with notes and CSV, share advice card, cost card, weather and crop-stage cards
- [x] "Did it work?": Better / Same / Worse on a due re-check, saved on the phone only (Outcome.kt); answers included in the diary CSV; "your re-checks that got better: X%" shows after 3 answers for a problem
- [x] "Please ask a person" card: after a Worse answer, when damage is severe, or when it is 5+ points worse than the earlier check. One-tap call to Kisan Call Centre 1800-180-1551 and send photo + result to an expert. It never changes the diagnosis.
- [x] Reminders: doze-safe alarm and restored after a reboot (BootReceiver). Still inexact: may be late by several minutes. Not exact-alarm permission on purpose.
- [x] 12 languages (EN, HI, BN, TE, MR, TA, GU, UR, KN, OR, ML, PA, AS): all advice text, screen text, the new "Did it work?" / "ask a person" text and the notification text. Machine translation, labelled beta.
- [x] Accuracy lab (accuracy_lab.py, docs\\ACCURACY_PLAN.md): per-crop confidence gate (chilli 0.90, pests 0.85), more look-alike warnings. Test-time averaging tried and rejected (no gain)
- [ ] **YOU** Field-photo test set (200+ KVK-labelled real photos) and extra field photos for rice, chilli, okra, pests; then retrain
- [ ] **Native-speaker review of every regional translation** (Assamese and Odia farm terms are the riskiest; 51 Odia and 18 Punjabi entries keep scientific names in Latin on purpose)
- [ ] Smart (Llama) answers are English only
- [ ] Weather and stage cards are keyword matches, not a forecast
- [ ] Tree probabilities and advice need KVK / agronomist review

## 2. Hub and tools
- [x] hub/hub_server.py (file server + /ask), make_manifest.py
- [ ] **YOU** run hub on a Raspberry Pi 5 and test /health, /ask, a phone download
- [ ] Light Llama build (vocabulary-trimmed): needs a custom ExecuTorch runner. Not started. Decide after the PSS log.

## 3. Training and models (all **YOU**, overnight on the RTX 3050)
- [ ] `cd D:\OCDD`; quick test: `python train.py --arch efficientnet_v2_s --interim interim_v2 --limit 20 --epochs 1 --batch 32 --out test_arch`
- [ ] `.\run_overnight.ps1` (PlantSeg download, leaf finder v3, lesion finder, run4)
- [ ] Send export_seg\report.txt, export_lesion\report.txt, training\run4\report.txt
- [ ] Copy new leafseg.onnx / lesionseg.onnx into app assets; export run4 (WSL, OCDD_ARCH=efficientnet_v2_s) if better than run3
- [ ] `.\train_crops.ps1`, then WSL `bash /mnt/d/OCDD/export_crops.sh`, then `python pack_crops.py`; keep a specialist only if better than the general model on that crop
- [ ] WSL: `OCDD_ONNX=... python /mnt/d/OCDD/benchmark.py` → benchmark_report.txt (the honest accuracy page for the presentation)

## 4. Release and tests (**YOU**)
- [ ] Android Studio: Rebuild, run `testDebugUnitTest` (incl. the new Outcomes tests), fix any red error (send it to me)
- [ ] Phone tests (checklist: docs\TEST_CHECKLIST.md; also reboot with a reminder set; set a Hindi/Odia language and check the notification; answer Worse): 8 GB Nothing, 4 GB OPPO (`adb logcat -d -b crash` if it crashes), pest photos, plot mode, mic, crop picker, extra photo
- [ ] Upload manifest.json + tokenizer.model + .pte to a PUBLIC GitHub release `models-v1`; test first-run download on a clean install
- [ ] Measure Llama memory from the log line "llama loaded: PSS" on each phone; update DeviceProfile.FULL_NEED_MB / LITE_NEED_MB
- [ ] Serve over https for release; remove usesCleartextTraffic

## 5. Content and safety
- [ ] PlantSeg / PlantWild class mapping (send plantseg_overview.txt), then new knowledge entries
- [ ] New crops/diseases: each needs a KB entry with CIB&RC-checked chemicals, symptom-tree questions, organic entry
- [ ] Re-verify every chemical against current CIB&RC label claims
- [ ] Native-speaker review of all Hindi text (including 114 symptom questions) and of every other language (see section 1)
- [ ] Verify dataset licences (PlantWild is non-commercial)
- [x] More languages: translation structure in assets/i18n/<code>.json (sha1 of the English text -> translation); workflow in /i18n tools (merge_parts.py, check_i18n.py)

## 6. Ideas not built
- Photo coach (live camera hints, needs CameraX), Grad-CAM "why" view, season/crop-stage alerts, KVK share bundle, voice-first mode, background download with notification.
