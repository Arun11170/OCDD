"""Larger-photo (320 px) model vs the general model, on the SAME unseen test photos, per crop. Run in WSL.
Both models only choose among that crop's own classes (no pests), so the comparison is fair. Writes compare_hires_report.txt."""
import csv, json, os, sys
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
from PIL import Image
import onnxruntime as ort
from torchvision import transforms as T
from train import unify

ROOT = "/mnt/d/OCDD"; A = ROOT + "/app/CropAdvisor/app/src/main/assets"; GATE = 0.80
CROPS = ["rice", "okra", "chilli", "groundnut"]
lc = lambda p: (lambda c: [c[k] for k in sorted(c, key=int)] if isinstance(c, dict) else c)(json.load(open(p)))
gcls = lc(A + "/classes.json"); gidx = {c: i for i, c in enumerate(gcls)}
hcls = lc(ROOT + "/training/hires4/classes.json"); hidx = {c: i for i, c in enumerate(hcls)}
gs = ort.InferenceSession(A + "/vision.onnx", providers=["CPUExecutionProvider"])
hs = ort.InferenceSession(ROOT + "/export_hires/vision_fp32.onnx", providers=["CPUExecutionProvider"])
tf224 = T.Compose([T.Resize(256), T.CenterCrop(224), T.ToTensor()])
tf320 = T.Compose([T.Resize(366), T.CenterCrop(320), T.ToTensor()])
loc = lambda p: ROOT + "/" + p.replace("D:\\OCDD\\", "").replace("\\", "/")
def sm(z): e = np.exp(z - z.max()); return e / e.sum()
rows = []
with open(ROOT + "/data/interim_v3/manifest.csv", newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        if r["split"] != "test": continue
        c = unify(r["crop"], r["label"])
        if c and c.split("__")[0] in CROPS and c in gidx and c in hidx and os.path.isfile(loc(r["path"])): r["cls"] = c; rows.append(r)
out = []; say = lambda s: (print(s, flush=True), out.append(s))
say("same unseen test photos, each model chooses only among the crop's own classes (no pests)")
say("%-10s %5s | %-24s | %-24s | %s" % ("crop", "n", "general: right / right|ans", "320px: right / right|ans", "difference"))
wins = 0
for crop in CROPS:
    R = [r for r in rows if r["cls"].startswith(crop + "__")]
    gm = np.array([c.startswith(crop + "__") for c in gcls]); hm = np.array([c.startswith(crop + "__") for c in hcls])
    G = []; H = []
    for r in R:
        im = Image.open(loc(r["path"])).convert("RGB"); y = r["cls"]
        zg = gs.run(None, {gs.get_inputs()[0].name: tf224(im).unsqueeze(0).numpy()})[0][0]
        zh = hs.run(None, {hs.get_inputs()[0].name: tf320(im).unsqueeze(0).numpy()})[0][0]
        pg = sm(np.where(gm, zg, -1e9)); ph = sm(np.where(hm, zh, -1e9))
        G.append((gcls[pg.argmax()] == y, pg.max())); H.append((hcls[ph.argmax()] == y, ph.max()))
    def st(L):
        ok = np.array([a for a, _ in L]); cf = np.array([c for _, c in L]); a = cf >= GATE
        return ok.mean(), (ok[a].mean() if a.sum() else 0)
    g, h = st(G), st(H); d = h[0] - g[0]; wins += d >= 0.03
    say("%-10s %5d | %10.1f%% %10.1f%% | %10.1f%% %10.1f%% | %+.1f points %s" % (crop, len(R), 100 * g[0], 100 * g[1], 100 * h[0], 100 * h[1], 100 * d, "<- better" if d >= 0.03 else ""))
say("\nVERDICT: " + ("USE the 320px model for the crops marked better (3+ points)." if wins else "NO real gain: keep the general model, do not ship the 320px model."))
open(ROOT + "/compare_hires_report.txt", "w").write("\n".join(out))
