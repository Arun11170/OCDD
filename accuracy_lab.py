"""Accuracy lab (no torch needed): python3 accuracy_lab.py [limit]
Measures on the held-out test split with the same ONNX the app ships:
 1. baseline top1 / answers-when-sure
 2. test-time averaging (flip, zoom) - all photos and only near the gate
 3. value of knowing the crop (what the crop picker does)
 4. per-crop gate: the lowest gate that gives >= TARGET accuracy when the app answers
 5. most-swapped class pairs (for hard-negative training and Confusable.kt)
Writes accuracy_lab_report.txt and accuracy_lab_probs.npz."""
import ast, csv, hashlib, json, os, random, sys, collections
import numpy as np
from PIL import Image
import onnxruntime as ort

ROOT = os.path.dirname(os.path.abspath(__file__))
ONNX = os.environ.get("OCDD_ONNX", ROOT + "/app/CropAdvisor/app/src/main/assets/vision.onnx")
INTERIM = os.environ.get("OCDD_INTERIM", "interim_v2")
LIMIT = int(sys.argv[1]) if len(sys.argv) > 1 else 1500
GATE = 0.80; TARGET = 0.92

src = ast.parse(open(ROOT + "/train.py", encoding="utf-8").read())
ns = {"os": os, "hashlib": hashlib}
for n in src.body:
    if (isinstance(n, ast.Assign) and any(getattr(t, "id", "") in ("TOMATO_MAP", "PEST_MERGE") for t in n.targets)) or (isinstance(n, ast.FunctionDef) and n.name == "unify"):
        exec(compile(ast.Module([n], []), "train", "exec"), ns)
unify = ns["unify"]
classes = json.load(open(ROOT + "/app/CropAdvisor/app/src/main/assets/classes.json"))
if isinstance(classes, dict): classes = [classes[k] for k in sorted(classes, key=int)]
cidx = {c: i for i, c in enumerate(classes)}
crop_of = np.array([c.split("__")[0] for c in classes])
cache = ROOT + "/data/interim/cache256"
cf = lambda p: os.path.join(cache, hashlib.md5(p.encode("utf-8")).hexdigest()[:20] + ".jpg")
rows = []
for r in csv.DictReader(open(ROOT + "/data/" + INTERIM + "/manifest.csv", newline="", encoding="utf-8")):
    c = unify(r["crop"], r["label"])
    if c in cidx and r["split"] == "test" and os.path.isfile(cf(r["path"])): r["y"] = cidx[c]; rows.append(r)
random.seed(1); random.shuffle(rows); rows = rows[:LIMIT]
sess = ort.InferenceSession(ONNX, providers=["CPUExecutionProvider"], sess_options=ort.SessionOptions())
inp = sess.get_inputs()[0].name

def prep(im, zoom=1.0, flip=False):
    w, h = im.size; s = 256 / min(w, h)
    im = im.resize((max(224, round(w * s)), max(224, round(h * s))), Image.BILINEAR)
    w, h = im.size; cw = int(224 / zoom); x0 = (w - cw) // 2; y0 = (h - cw) // 2
    im = im.crop((x0, y0, x0 + cw, y0 + cw)).resize((224, 224), Image.BILINEAR)
    if flip: im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return np.asarray(im, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0

def probs(x):
    z = sess.run(None, {inp: x})[0][0]; e = np.exp(z - z.max()); return e / e.sum()

import time
CACHE = ROOT + "/accuracy_lab_cache.json"; BUDGET = float(os.environ.get("OCDD_BUDGET", "140"))
done = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
t0 = time.time()
for i, r in enumerate(rows):
    if r["path"] in done: continue
    if time.time() - t0 > BUDGET: break
    im = Image.open(cf(r["path"])).convert("RGB")
    done[r["path"]] = [probs(prep(im)).tolist(), probs(prep(im, flip=True)).tolist(), probs(prep(im, zoom=1.25)).tolist()]
json.dump(done, open(CACHE, "w"))
left = sum(1 for r in rows if r["path"] not in done)
print("done %d / %d" % (len(rows) - left, len(rows)))
if left: sys.exit(0)
P0 = np.array([done[r["path"]][0] for r in rows]); Pf = np.array([done[r["path"]][1] for r in rows]); Pz = np.array([done[r["path"]][2] for r in rows])
y = np.array([r["y"] for r in rows]); dom = np.array([r["domain"] for r in rows])
np.savez(ROOT + "/accuracy_lab_probs.npz", P0=P0, Pf=Pf, Pz=Pz, y=y)
out = []
def say(s=""): print(s); out.append(s)
def rep(name, P, m=None):
    m = np.ones(len(y), bool) if m is None else m
    c = P.max(1); ok = P.argmax(1) == y; a = m & (c >= GATE)
    say("%-34s n=%4d top1 %.3f | answers %.1f%%, right %.3f when it answers" % (name, m.sum(), ok[m].mean(), 100 * a.sum() / m.sum(), ok[a].mean() if a.sum() else 0))
say("model %s, %d test photos (field %d, lab %d)" % (ONNX, len(y), (dom == "field").sum(), (dom == "lab").sum()))
say("\n== 1/2. test-time averaging ==")
Pfl = (P0 + Pf) / 2; Pall = (P0 + Pf + Pz) / 3
near = (P0.max(1) > 0.35) & (P0.max(1) < 0.92)
Pnear = np.where(near[:, None], Pall, P0)
for nm, P in (("baseline", P0), ("flip average", Pfl), ("flip+zoom average", Pall), ("flip+zoom only when 0.35-0.92", Pnear)):
    rep(nm, P); rep(nm + " [field]", P, dom == "field")
say("photos where averaging runs in the 'near' version: %.1f%%" % (100 * near.mean()))
say("\n== 3. value of knowing the crop (crop picker) ==")
Pc = P0.copy()
for i in range(len(y)):
    allow = (crop_of == crop_of[y[i]]) | (crop_of == "pest")
    q = np.where(allow, P0[i], 0); s = q.sum()
    if s > 1e-6: Pc[i] = q / s
rep("any crop", P0); rep("crop known (oracle)", Pc)
rep("any crop [field]", P0, dom == "field"); rep("crop known [field]", Pc, dom == "field")
say("\n== 4. per-crop gate (lowest gate with right >= %.2f when it answers) ==" % TARGET)
cr = np.array([classes[t].split("__")[0] for t in y]); conf = P0.max(1); ok0 = P0.argmax(1) == y
rec = {}
for c in sorted(set(cr)):
    m = cr == c; best = None
    for g in np.arange(0.5, 0.99, 0.01):
        a = m & (conf >= g)
        if a.sum() >= 10 and ok0[a].mean() >= TARGET: best = round(float(g), 2); break
    a80 = m & (conf >= GATE)
    say("%-10s n=%4d right@0.80 %.3f (answers %.0f%%) -> gate for %.2f: %s" % (c, m.sum(), ok0[a80].mean() if a80.sum() else 0, 100 * a80.sum() / max(1, m.sum()), TARGET, best if best else "not reachable (needs more data/training)"))
    rec[c] = best
json.dump(rec, open(ROOT + "/accuracy_lab_gates.json", "w"), indent=1)
say("\n== 5. most-swapped pairs (true -> predicted, wrong answers only) ==")
cnt = collections.Counter((classes[a], classes[b]) for a, b in zip(y, P0.argmax(1)) if a != b)
for (a, b), n in cnt.most_common(15): say("%3d  %s -> %s" % (n, a, b))
open(ROOT + "/accuracy_lab_report.txt", "w", encoding="utf-8").write("\n".join(out))
