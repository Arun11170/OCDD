"""General model vs each crop specialist on the SAME photos. Run inside WSL (see compare_models.bat).
For every crop: test photos of that crop + pests (+ field_test photos). Three rows per crop:
  general      = the 95-class general model, as is
  general+crop = the general model with the other crops' classes switched off (what the app does after the farmer picks a crop)
  specialist   = the crop's own small model
Writes compare_report.txt with a verdict per crop."""
import csv, json, os, sys
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
from PIL import Image
import onnxruntime as ort
from torchvision import transforms as T
from train import unify, cpath

ROOT = "/mnt/d/OCDD"; GATE = 0.80
CROPS = "apple banana capsicum chilli grape groundnut maize mango okra potato rice sugarcane tomato wheat".split()
only = sys.argv[1:] or CROPS
def load_classes(p):
    c = json.load(open(p))
    return [c[k] for k in sorted(c, key=int)] if isinstance(c, dict) else c
gcls = load_classes(ROOT + "/app/CropAdvisor/app/src/main/assets/classes.json"); gidx = {c: i for i, c in enumerate(gcls)}
gs = ort.InferenceSession(ROOT + "/export_v5/vision_fp32.onnx", providers=["CPUExecutionProvider"])
tf = T.Compose([T.Resize(256), T.CenterCrop(224), T.ToTensor()])
cache_win = r"D:\OCDD\data\interim\cache256"
cf = lambda p: cpath(cache_win, p).replace("\\", "/").replace("D:/", "/mnt/d/")
rows = []
with open(ROOT + "/data/interim_v3/manifest.csv", newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        c = unify(r["crop"], r["label"])
        if c in gidx and r["split"] in ("test", "field_test") and os.path.isfile(cf(r["path"])): r["c"] = c; rows.append(r)
def softmax(z): e = np.exp(z - z.max()); return e / e.sum()
def stats(ok, conf):
    a = conf >= GATE
    return ok.mean(), (ok[a].mean() if a.sum() else 0), a.mean()
out = []
def say(s): print(s, flush=True); out.append(s)
say("same photos for every model. 'right' = top-1 accuracy; 'answered' = photos with confidence >= %.2f; 'right|answered' = accuracy on those." % GATE)
say("%-10s %-12s %5s  %7s %9s %14s" % ("crop", "model", "n", "right", "answered", "right|answered"))
verdicts = []
for crop in only:
    sp = ROOT + "/training/crop_%s/classes.json" % crop; op = ROOT + "/export_crops/%s/vision_fp32.onnx" % crop
    if not (os.path.isfile(sp) and os.path.isfile(op)): say("%s: files missing, skipped" % crop); continue
    scls = load_classes(sp); sidx = {c: i for i, c in enumerate(scls)}
    ss = ort.InferenceSession(op, providers=["CPUExecutionProvider"])
    R = [r for r in rows if r["c"].split("__")[0] in (crop, "pest") and r["c"] in sidx]
    if len(R) < 20: say("%s: only %d photos, skipped" % (crop, len(R))); continue
    mask = np.array([c.split("__")[0] in (crop, "pest") for c in gcls])
    ok_g, c_g, ok_m, c_m, ok_s, c_s = [], [], [], [], [], []
    for r in R:
        x = tf(Image.open(cf(r["path"])).convert("RGB")).unsqueeze(0).numpy()
        zg = gs.run(None, {gs.get_inputs()[0].name: x})[0][0]
        zs = ss.run(None, {ss.get_inputs()[0].name: x})[0][0]
        pg = softmax(zg); pm = softmax(np.where(mask, zg, -1e9)); ps = softmax(zs)
        yg = gidx[r["c"]]; ys = sidx[r["c"]]
        ok_g.append(pg.argmax() == yg); c_g.append(pg.max())
        ok_m.append(pm.argmax() == yg); c_m.append(pm.max())
        ok_s.append(ps.argmax() == ys); c_s.append(ps.max())
    res = {}
    for name, ok, cf_ in (("general", ok_g, c_g), ("general+crop", ok_m, c_m), ("specialist", ok_s, c_s)):
        res[name] = stats(np.array(ok), np.array(cf_))
        say("%-10s %-12s %5d  %6.1f%% %8.1f%% %13.1f%%" % (crop, name, len(R), 100 * res[name][0], 100 * res[name][2], 100 * res[name][1]))
    d = res["specialist"][0] - res["general+crop"][0]
    v = "KEEP specialist" if d >= 0.02 else ("use general" if d <= 0.005 else "tie - use general (one less file)")
    verdicts.append((crop, d, v)); say("   -> specialist minus general+crop: %+.1f points  => %s\n" % (100 * d, v))
say("VERDICT (a specialist must win by 2+ points to be worth its 17 MB):")
for crop, d, v in verdicts: say("  %-10s %+5.1f  %s" % (crop, 100 * d, v))
open(ROOT + "/compare_report.txt", "w").write("\n".join(out))
