"""100 unseen test photos per crop (+100 pests) through the models inside the app, same steps as the app.
Writes test100_report.txt and test100_wrong.csv. Run in WSL via test_100.bat."""
import csv, json, os, random, sys, collections
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
from PIL import Image
import onnxruntime as ort
from train import unify

ROOT = "/mnt/d/OCDD"; A = ROOT + "/app/CropAdvisor/app/src/main/assets"; GATE = 0.80
N = int(os.environ.get("PER_CROP", "100"))
classes = json.load(open(A + "/classes.json"))
if isinstance(classes, dict): classes = [classes[k] for k in sorted(classes, key=int)]
cidx = {c: i for i, c in enumerate(classes)}
vis = ort.InferenceSession(A + "/vision.onnx", providers=["CPUExecutionProvider"])
les = ort.InferenceSession(A + "/lesionseg.onnx", providers=["CPUExecutionProvider"])
loc = lambda p: ROOT + "/" + p.replace("D:\\OCDD\\", "").replace("\\", "/")

def probs(img):
    w, h = img.size; s = 256 / min(w, h); nw, nh = max(224, round(w * s)), max(224, round(h * s))
    im = img.resize((nw, nh), Image.BILINEAR); x0, y0 = (nw - 224) // 2, (nh - 224) // 2
    x = np.asarray(im.crop((x0, y0, x0 + 224, y0 + 224)), dtype=np.float32).transpose(2, 0, 1)[None] / 255
    z = vis.run(None, {vis.get_inputs()[0].name: x})[0][0]; p = np.exp(z - z.max()); return p / p.sum()
def lesion_pct(img):
    S = 512; w, h = img.size; k = S / max(w, h); nw, nh = max(1, int(w * k)), max(1, int(h * k))
    im = np.asarray(img.resize((nw, nh), Image.BILINEAR), dtype=np.float32) / 255
    c = np.empty((S, S, 3), np.float32); c[:] = np.array([124, 116, 104], np.float32) / 255
    ox, oy = (S - nw) // 2, (S - nh) // 2; c[oy:oy + nh, ox:ox + nw] = im
    pr = les.run(None, {les.get_inputs()[0].name: c.transpose(2, 0, 1)[None]})[0][0][0]
    return 100.0 * (pr[oy:oy + nh, ox:ox + nw] > 0.5).mean()

rows = []
with open(ROOT + "/data/interim_v3/manifest.csv", newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        if r["split"] != "test": continue
        c = unify(r["crop"], r["label"])
        if c in cidx and os.path.isfile(loc(r["path"])): r["cls"] = c; rows.append(r)
random.seed(11); by = collections.defaultdict(lambda: collections.defaultdict(list))
for r in rows: by[r["cls"].split("__")[0]][r["cls"]].append(r)
def sample(crop):
    pools = []
    for cl, lst in by[crop].items():
        fld = [r for r in lst if r["domain"] == "field"] or lst
        random.shuffle(fld); pools.append(fld)
    out = []
    while len(out) < N and any(pools):
        for p in pools:
            if p and len(out) < N: out.append(p.pop())
    return out

out = []; wrong = []
def say(s): print(s, flush=True); out.append(s)
say("100 unseen test photos per crop, same models and steps as the app. 'free' = no crop chosen; 'crop' = farmer chose the crop (crop+pests only).")
say("%-10s %4s | %6s %6s | %8s %8s | %12s %12s | %s" % ("crop", "n", "free", "crop", "answered", "right|ans", "healthy->red", "sick->0%dmg", "most common mistakes (crop chosen)"))
tot = collections.Counter()
for crop in sorted(by):
    R = sample(crop); mask = np.array([c.split("__")[0] in (crop, "pest") for c in classes])
    a = b = ans = ansok = hf = hn = sf = sn = 0; conf = collections.Counter()
    for r in R:
        img = Image.open(loc(r["path"])).convert("RGB"); p = probs(img); y = cidx[r["cls"]]
        q = np.where(mask, p, 0) if crop != "pest" else p; q = q / q.sum()
        ok_free = p.argmax() == y; ok_crop = q.argmax() == y; a += ok_free; b += ok_crop
        if q.max() >= GATE: ans += 1; ansok += ok_crop
        if not ok_crop: conf["%s -> %s" % (r["cls"].split("__")[1], classes[q.argmax()].split("__", 1)[1] if classes[q.argmax()].split("__")[0] == crop or crop == "pest" else classes[q.argmax()])] += 1; wrong.append((crop, r["cls"], classes[q.argmax()], round(float(q.max()), 2), r["path"]))
        d = lesion_pct(img) if crop != "pest" else 0
        if crop != "pest":
            if "healthy" in r["cls"]: hn += 1; hf += d > 1.0
            else: sn += 1; sf += d < 0.5
    n = len(R); tot.update(n=n, a=a, b=b, ans=ans, ansok=ansok)
    say("%-10s %4d | %5.1f%% %5.1f%% | %7.1f%% %7.1f%% | %5d/%-5d %6d/%-5d | %s" % (crop, n, 100 * a / n, 100 * b / n, 100 * ans / n, 100 * ansok / max(ans, 1), hf, hn, sf, sn, "; ".join("%s x%d" % kv for kv in conf.most_common(3))))
say("\nALL %d photos: right free %.1f%%, right with crop chosen %.1f%%, answers %.1f%% of photos and is right %.1f%% when it answers" % (tot["n"], 100 * tot["a"] / tot["n"], 100 * tot["b"] / tot["n"], 100 * tot["ans"] / tot["n"], 100 * tot["ansok"] / max(tot["ans"], 1)))
say("healthy->red = healthy leaves with more than 1% of the photo flagged as damage; sick->0%dmg = diseased leaves where almost nothing was marked.")
open(ROOT + "/test100_report.txt", "w").write("\n".join(out))
with open(ROOT + "/test100_wrong.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f); w.writerow(["crop", "true", "app_said", "confidence", "photo"]); w.writerows(wrong)
