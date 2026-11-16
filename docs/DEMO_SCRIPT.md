# Demo video script (about 3 minutes, one continuous screen recording)

Prepare: fresh install, models already on the phone, **airplane mode ON and visible in the status bar for the whole video**.
Have four photos ready in the gallery: (1) a clear diseased leaf (e.g. tomato early blight), (2) a clear healthy leaf, (3) a blurry/dark leaf, (4) a photo that is not a plant (a hand or a wall).

| Time | Show | Say |
|---|---|---|
| 0:00 | Home screen, airplane mode visible | "Field Assistant works with no internet. Airplane mode is on. Everything runs on this phone." |
| 0:15 | Take/choose photo 1 (diseased leaf) | "A farmer photographs an affected leaf." |
| 0:30 | Result: problem name, confidence, damage % | "The vision model names the likely problem, shows how sure it is, and measures how much of the leaf is damaged." |
| 0:50 | Advisory card appears (Llama) | "Now a second pipeline: Llama 3.2 1B, running through ExecuTorch on the phone's Arm CPU, writes the advisory from the vision result and from verified agriculture facts." |
| 1:15 | Point at 'checked against the verified facts' + tokens/s line | "Every answer is checked: no invented chemicals, doses or numbers. If it fails the check, the verified text is shown instead." |
| 1:30 | Switch language to Hindi | "Switching to Hindi: Llama writes the advisory in Hindi." |
| 1:50 | Switch to Tamil | "For languages Llama does not officially support, the app shows the verified translated advice." |
| 2:05 | Photo 2 (healthy leaf) | "A healthy leaf is reported as healthy." |
| 2:20 | Photo 3 (blurry) then photo 4 (hand) | "Unclear photo: the app does not guess. A photo with no plant: it says no leaf found and asks for a retake." |
| 2:40 | A severe result | "For severe damage it tells the farmer to contact an extension worker or the Kisan Call Centre. This is a likely cause, not a confirmed diagnosis." |
| 2:55 | Settings > Self-check, all green | "All models are stored on the device. Source code, report and setup steps are in the repository." |

Rules: do not edit out the airplane-mode icon; do not cut away during Llama's generation (show the real wait); if a result is wrong on camera, keep it and explain the uncertainty handling.
