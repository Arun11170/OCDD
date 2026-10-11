# Crop Advisor (Android, Kotlin) - first build

## Put it on the phone
1. Copy the disease model into the app:
   `Copy-Item D:\OCDD\export\vision_int8.pte D:\OCDD\app\CropAdvisor\app\src\main\assets\`
2. Android Studio: File > Open > `D:\OCDD\app\CropAdvisor`. Gradle = your local install (8.11.1). Wait for sync.
3. Pick the Arm phone, press Run.
4. Llama ("Smart answers") looks for the model in `/data/local/tmp/llama` (already pushed) - no extra step.

## What is inside
- Photo -> disease model (MobileNetV3 int8, ExecuTorch) -> confidence gate 0.80 -> damage % (OpenCV) -> verified advice (kb.json / kb_hi.json) -> voice.
- Below the gate the app says "not sure" and shows no advice until the farmer picks a possibility.
- Ask section: quick buttons from the verified advice (every phone); "Smart answers" switch uses Llama in a separate process, English only, with a safety check (blocks numbers, chemicals, organisms and disease names that are not in the facts). Dose questions are never answered.
- No INTERNET permission.

## If the build complains
- `LlmService.kt`: "overrides nothing" on `onError` -> delete that one line.
- `LlmService.kt`: unresolved `LlmModule` -> paste the error; the AAR API changed.
- Duplicate `libc++_shared.so` -> already handled with pickFirsts.

## Added after the first release
- Symptom tree (assets/symptom_tree.json, Checks.kt): questions per crop, Yes/No prune the candidate problems.
- One general model for all crops and pests (Classifier.predict(photo)); no crop picker. Pest answers are refused when the model also believes the leaf may be healthy.
- First-run Llama download (SetupScreen, ModelDownloader, DeviceProfile) from ModelDownloader.DEFAULT_SERVER.
- Village hub (hub/hub_server.py, HubClient.kt) for phones that cannot run Llama.
- The app now has the INTERNET permission, used only for the farmer-started model download and the hub on the local Wi-Fi.
See docs/IMPLEMENTATION_PLAN.md for what is done and what is open.
