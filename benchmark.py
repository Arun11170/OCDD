"""Honest accuracy report. Run INSIDE WSL after export:
  source ~/et/bin/activate
  OCDD_ONNX=/mnt/d/OCDD/export_v4/vision_fp32.onnx OCDD_INTERIM=interim_v2 python /mnt/d/OCDD/benchmark.py
Writes benchmark_report.txt: accuracy by split and domain, how often the app says 'not sure', accuracy when it answers,
calibration (ECE), weakest classes, and how many wrong/unsure photos the symptom tree could rescue (best case: farmer answers
exactly as the tables say)."""
import csv, json, os, random, sys, collections
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
from PIL import Image
import onnxruntime as ort
from torchvision import transforms as T
from train import unify, cpath
import tree_sim

ROOT = "/mnt/d/OCDD"
ONNX = os.environ.get("OCDD_ONNX", ROOT + "/export_v3/vision_fp32.onnx")
INTERIM = os.environ.get("OCDD_INTERIM", "interim_v2")
GATE = 0.80
classes = json.load(open(ROOT + "/app/CropAdvisor/app/src/main/assets/classes.json"))
if isinstance(classes, dict): classes = [classes[k] for k in sorted(classes, key=int)]
cidx = {c: i for i, c in enumerate(classes)}
sess = ort.InferenceSession(ONNX, providers=["CPUExecutionProvider"])
tf = T.Compose([T.Resize(256), T.CenterCrop(224), T.ToTensor()])
cache_win = r"D:\OCDD\data\interim\cache256"
cf = lambda p: cpath(cache_win, p).replace("\\", "/").replace("D:/", "/mnt/d/")
rows = []
with open(ROOT + "/data/" + INTERIM + "/manifest.csv", newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        c = unify(r["crop"], r["label"])
        if c in cidx and r["split"] in ("test", "field_test") and os.path.isfile(cf(r["path"])): r["y"] = cidx[c]; r["c"] = c; rows.append(r)
random.seed(0)
lim = int(os.environ.get("OCDD_LIMIT", "4000"))
test = [r for r in rows if r["split"] == "test"]; random.shuffle(test)
rows = test[:lim] + [r for r in rows if r["split"] == "field_test"]
out = []
def say(s): print(s); out.append(s)
P = []
for r in rows:
    x = tf(Image.open(cf(r["path"])).convert("RGB")).unsqueeze(0).numpy()
    z = sess.run(None, {sess.get_inputs()[0].name: x})[0][0]
    e = np.exp(z - z.max()); P.append(e / e.sum())
P = np.array(P); y = np.array([r["y"] for r in rows])
conf = P.max(1); pred = P.argmax(1); ok = pred == y
top3 = np.array([y[i] in np.argsort(-P[i])[:3] for i in range(len(y))])
say("model: %s   photos: %d" % (ONNX, len(y)))
def block(name, m):
    if m.sum() == 0: return
    a = m & (conf >= GATE)
    say("%-14s n=%5d  top1 %.3f  top3 %.3f | answers %.1f%% of photos, right %.3f when it answers | says 'not sure' %.1f%%" % (
        name, m.sum(), ok[m].mean(), top3[m].mean(), 100 * a.sum() / m.sum(), ok[a].mean() if a.sum() else 0, 100 * (m & (conf < GATE)).sum() / m.sum()))
sp = np.array([r["split"] for r in rows]); dm = np.array([r["domain"] for r in rows])
block("ALL", np.ones(len(y), bool)); block("test", sp == "test"); block("field_test", sp == "field_test")
for d in sorted(set(dm)): block("domain " + d, dm == d)
cr = np.array([r["c"].split("__")[0] for r in rows])
say("\nper crop (top1):")
for c in sorted(set(cr)): block(c, cr == c)
bins = np.linspace(0, 1, 11); ece = 0
for i in range(10):
    m = (conf > bins[i]) & (conf <= bins[i + 1])
    if m.sum(): ece += m.mean() * abs(ok[m].mean() - conf[m].mean())
say("\ncalibration error (ECE, lower is better): %.3f" % ece)
cc = collections.defaultdict(lambda: [0, 0])
for i, r in enumerate(rows): cc[r["c"]][0] += 1; cc[r["c"]][1] += int(ok[i])
worst = sorted(((v[1] / v[0], k, v[0]) for k, v in cc.items() if v[0] >= 5))[:12]
say("weakest classes: " + "; ".join("%s %.2f (n=%d)" % (k, a, n) for a, k, n in worst))
rng = random.Random(2); need = rescued = 0
for i in range(len(y)):
    if ok[i] and conf[i] >= GATE: continue
    ranked = {classes[j]: float(P[i][j]) for j in np.argsort(-P[i])[:6]}
    res, _ = tree_sim.run(ranked, tree_sim.oracle_for(classes[y[i]], rng))
    need += 1; rescued += res == classes[y[i]]
say("\nphotos that were wrong or 'not sure': %d. Symptom tree reaches the right problem in %.1f%% of them (best case, farmer answers as the tables say; real farmers will do worse)." % (need, 100 * rescued / max(need, 1)))
open(ROOT + "/benchmark_report.txt", "w").write("\n".join(out))
