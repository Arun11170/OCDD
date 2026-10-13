"""Takes one photo on a Raspberry Pi. Tries, in order: Picamera2 (Camera Module 2/3, HQ), rpicam-still, a USB webcam via OpenCV."""
import os, subprocess, tempfile
from PIL import Image


def capture(path=None, size=(2304, 1296)):
    """Returns a PIL image (and saves it to `path` if given). Raises RuntimeError with a plain message if no camera works."""
    errors = []
    try:
        from picamera2 import Picamera2
        cam = Picamera2()
        cam.configure(cam.create_still_configuration(main={"size": size}))
        cam.start()
        try:
            cam.set_controls({"AfMode": 2})      # continuous autofocus (Camera Module 3); ignored by cameras without it
        except Exception:
            pass
        import time; time.sleep(1.5)             # let exposure and focus settle
        arr = cam.capture_array()
        cam.stop(); cam.close()
        im = Image.fromarray(arr[:, :, :3][:, :, ::-1] if arr.shape[2] >= 3 else arr)   # BGR -> RGB
        if path: im.save(path, quality=92)
        return im
    except Exception as e:
        errors.append("picamera2: %s" % e)
    try:
        out = path or os.path.join(tempfile.gettempdir(), "ocdd_capture.jpg")
        for exe in ("rpicam-still", "libcamera-still"):
            try:
                subprocess.run([exe, "-n", "-t", "1500", "--autofocus-mode", "continuous", "-o", out], check=True, capture_output=True, timeout=30)
                return Image.open(out).convert("RGB")
            except FileNotFoundError:
                continue
        errors.append("rpicam-still: not installed")
    except Exception as e:
        errors.append("rpicam-still: %s" % e)
    try:
        import cv2
        cap = cv2.VideoCapture(0)
        ok, frame = cap.read(); cap.release()
        if ok:
            im = Image.fromarray(frame[:, :, ::-1])
            if path: im.save(path, quality=92)
            return im
        errors.append("webcam: no picture")
    except Exception as e:
        errors.append("webcam: %s" % e)
    raise RuntimeError("No camera worked. Check the ribbon cable and that the camera is enabled. Details: " + " | ".join(errors))
