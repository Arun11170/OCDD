# Crop Advisor on Raspberry Pi 5

Same photo model, advice (CIB&RC-checked, no doses) and 13 languages as the phone app, running offline on a Pi 5.
Code: `pi5/`. It reads the app's own files (`app/CropAdvisor/app/src/main/assets`), so a model update for the phone updates the Pi.

## Hardware
- Raspberry Pi 5, 8 GB (4 GB works for the photo model), 27 W power supply, active cooler, 32 GB+ microSD or an NVMe SSD.
- Camera: Camera Module 3 (autofocus) is best; any Pi camera or a USB webcam also works.
- Screen: the official 7" touch display, or no screen at all: open the web page from a phone on the same Wi-Fi/hotspot.

## Install (about 15 minutes)
1. Flash **Raspberry Pi OS (64-bit)** with Raspberry Pi Imager; enable SSH and Wi-Fi in the Imager settings.
2. On the Pi: `git clone https://github.com/Arun11170/OCDD.git && cd OCDD && bash pi5/setup_pi.sh`
3. The script installs everything, runs a self-test on the 13 test photos, and starts the app at every boot.
4. Open `http://<pi-address>:8000` on a phone, or `chromium-browser --kiosk http://localhost:8000` on the Pi's screen.

## Use
- **Take photo with the Pi camera**: one tap; result in about a second.
- **Choose or take a photo here**: from the phone's browser (its camera works too).
- Result: green card for healthy, yellow to red by confidence, damage estimate, what to see/do, spray options (label only, no dose), look-alikes, read aloud (the browser's own voice; Hindi and others need that voice installed on the viewing device).
- Photos and history stay in `~/ocdd_data`.

## Command line (no screen)
`python pi5/cli.py leaf.jpg --lang hi`     `python pi5/cli.py --selftest`

## Known limits
- Same photo model as the phone: it can be wrong on plants and backgrounds it was never trained on (see ACCURACY_PLAN.md).
- The Smart (Llama) Q&A and voice commands are phone-only for now. On the Pi, offline voice would need Vosk/Whisper and a Pi-format Llama (GGUF): a later step.
- Not tested on a real Pi yet: the code ran on a Linux PC. Send me any error from `journalctl -u ocdd -n 50`.
