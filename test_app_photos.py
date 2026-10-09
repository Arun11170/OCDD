"""Runs the test photos through the SAME model files that are inside the app (assets/vision.onnx, lesionseg.onnx),
with the same steps the app uses, and prints expected vs predicted. No phone needed. Run inside WSL (test_app_photos.bat)."""
import json, os, sys
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
from PIL import Image
import onnxruntime as ort
from train import unify

ROOT = "/mnt/d/OCDD"; A = ROOT + "/app/CropAdvisor/app/src/main/assets"
PH = ROOT + "/app_test_photos"; OUT = ROOT + "/app_test_results"; GATE = 0.80
os.makedirs(OUT, exist_ok=True)
classes = json.load(open(A + "/classes.json"))
if isinstance(classes, dict): classes = [classes[k] for k in sorted(classes, key=int)]
vis = ort.InferenceSession(A + "/vision.onnx", providers=["CPUExecutionProvider"])
les = ort.InferenceSession(A + "/lesionseg.onnx", providers=["CPUExecutionProvider"])

def classify(img, crop=None):
    w, h = img.size; s = 256 / min(w, h)
    nw, nh = max(224, round(w * s)), max(224, round(h * s))
    im = img.resize((nw, nh), Image.BILINEAR); x0, y0 = (nw - 224) // 2, (nh - 224) // 2
    x = np.asarray(im.crop((x0, y0, x0 + 224, y0 + 224)), dtype=np.float32).transpose(2, 0, 1)[None] / 255
    z = vis.run(None, {vis.get_inputs()[0].name: x})[0][0]; p = np.exp(z - z.max()); p /= p.sum()
    if crop:
        m = np.array([c.split("__")[0] in (crop, "pest") for c in classes]); q = np.where(m, p, 0); q /= q.sum()
    else: q = p
    top = np.argsort(-q)[:3]
    return [(classes[i], float(q[i])) for i in top]

def lesion(img):
    S = 512; w, h = img.size; k = S / max(w, h); nw, nh = max(1, int(w * k)), max(1, int(h * k))
    im = np.asarray(img.resize((nw, nh), Image.BILINEAR), dtype=np.float32) / 255
    canvas = np.empty((S, S, 3), np.float32); canvas[:] = np.array([124, 116, 104], np.float32) / 255
    ox, oy = (S - nw) // 2, (S - nh) // 2; canvas[oy:oy + nh, ox:ox + nw] = im
    pr = les.run(None, {les.get_inputs()[0].name: canvas.transpose(2, 0, 1)[None]})[0][0][0]
    m = pr[oy:oy + nh, ox:ox + nw] > 0.5
    return m, 100.0 * m.mean()

def band(p): return "none" if p < 0.5 else ("Low" if p < 5 else ("Moderate" if p < 20 else "Severe"))
rows = []; ok_n = ok_c = n = 0
for f in sorted(os.listdir(PH)):
    if not f.lower().endswith((".jpg", ".jpeg", ".png")): continue
    stem = os.path.splitext(f)[0]; crop, label = stem.split("__", 1)
    img = Image.open(PH + "/" + f).convert("RGB")
    t = classify(img); tc = classify(img, None if crop == "pest" else crop)
    if label == "healthy": hit = lambda c: c.split("__")[0] == crop and "healthy" in c
    else:
        exp = unify(crop, label); hit = lambda c, e=exp: c == e
    m, pct = lesion(img)
    a, b = hit(t[0][0]), hit(tc[0][0]); n += 1; ok_n += a; ok_c += b
    rows.append((f, t, tc, pct, a, b))
    ov = np.asarray(img).copy(); mm = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize(img.size)) > 0
    ov[mm] = (0.5 * ov[mm] + 0.5 * np.array([255, 0, 0])).astype(np.uint8); Image.fromarray(ov).save(OUT + "/" + stem + "_damage.jpg")
lines = ["%-44s %-3s %-3s %-34s %5s  %s" % ("photo", "any", "crp", "app answer (no crop chosen)", "conf", "damage")]
for f, t, tc, pct, a, b in rows:
    lines.append("%-44s %-3s %-3s %-34s %4.0f%%  %.1f%% (%s)%s" % (f[:44], "OK" if a else "NO", "OK" if b else "NO", t[0][0][:34], 100 * t[0][1], pct, band(pct), "" if t[0][1] >= GATE else "  [says not sure]"))
lines += ["", "right with no crop chosen: %d / %d    right with crop chosen: %d / %d" % (ok_n, n, ok_c, n),
          "damage pictures (red = found damage) are in D:\\OCDD\\app_test_results"]
open(ROOT + "/app_test_report.txt", "w").write("\n".join(lines)); print("\n".join(lines))
