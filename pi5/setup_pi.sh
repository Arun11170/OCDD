#!/usr/bin/env bash
# One-time setup on Raspberry Pi OS (64-bit, Bookworm) for Pi 5. Run from the repo:   bash pi5/setup_pi.sh
set -e
cd "$(dirname "$0")/.."
REPO="$(pwd)"
echo "== 1/4 system packages (camera, OpenCV, voice)"
sudo apt update
sudo apt install -y python3-venv python3-pip python3-picamera2 python3-opencv python3-numpy python3-pil espeak-ng git
echo "== 2/4 python environment (uses the system camera and OpenCV packages)"
python3 -m venv --system-site-packages "$HOME/ocdd-venv"
"$HOME/ocdd-venv/bin/pip" install --upgrade pip
"$HOME/ocdd-venv/bin/pip" install onnxruntime flask
echo "== 3/4 self-test on the 13 test photos (no camera needed)"
"$HOME/ocdd-venv/bin/python" pi5/cli.py --selftest | tail -25
echo "== 4/4 start at every boot"
sudo tee /etc/systemd/system/ocdd.service >/dev/null <<UNIT
[Unit]
Description=Crop Advisor
After=network.target
[Service]
User=$USER
WorkingDirectory=$REPO
ExecStart=$HOME/ocdd-venv/bin/python $REPO/pi5/server.py
Restart=always
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now ocdd.service
echo
echo "Done. Open  http://$(hostname -I | awk '{print $1}'):8000  from a phone on the same Wi-Fi, or  http://localhost:8000  on the Pi."
echo "Full-screen on the Pi's own screen:  chromium-browser --kiosk http://localhost:8000"
