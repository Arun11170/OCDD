# Offline audit (what touches the network, and what does not)

Checked by reading the source (search for HttpURLConnection, URL(), http, Socket in app/src/main/java).

| Part of the workflow | Network code? | Evidence |
|---|---|---|
| Camera capture, gallery pick | No | CoachCamera.kt, ImageUtil.kt: no network classes |
| Vision (EfficientNet-V2-S, ONNX Runtime, leaf/lesion segmentation, severity) | No | Classifier.kt, LeafSeg.kt, LesionSeg.kt, Severity.kt: models bundled in assets, loaded from local files |
| Advisory (Llama 3.2 1B, ExecuTorch, own process) | No | MainViewModel.advise(), LlmClient.kt, LlmService.kt: local files and a local bound service only; the advisory never calls the hub |
| Verified knowledge base, translations, voice read-out | No | kb*.json and i18n/*.json in assets; Android's own offline text-to-speech |
| Output safety check | No | Grounding.kt is pure Kotlin |
| Model download (Settings / first-run setup) | Yes, only when the user presses download | ModelDownloader.kt: GitHub release; needed once, before the demo |
| Village hub (optional, for phones that cannot run Llama) | Yes, LAN only, only in the question box when a hub address is set | HubClient.kt; not used by the advisory |

The INTERNET permission exists only for the two optional parts above.

## How to prove it on video / in the report
1. Install, run the model download once, then turn **airplane mode on** (Wi-Fi and mobile data off).
2. Force-stop and reopen the app; run Settings > Self-check (all green) and scan the sample photos.
3. Optional hard evidence: `adb shell dumpsys netstats` before and after a run shows no new app traffic, or run with the permission revoked: `adb shell pm revoke com.cropadvisor android.permission.INTERNET` is not possible for normal permissions, so use airplane mode plus a firewall app / `adb shell cmd netpolicy add restrict-background-blacklist <uid>` if a stricter proof is wanted.
