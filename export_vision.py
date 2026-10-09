"""Convert the trained model to ExecuTorch (.pte) for Android. Run INSIDE WSL (Ubuntu).

  source ~/et/bin/activate
  python /mnt/d/OCDD/export_vision.py

Makes in /mnt/d/OCDD/export/ :
  vision_int8.pte   (small, fast - the one the app uses)
  vision_fp32.pte   (fallback, bigger)
  classes.json, export_report.txt
The model takes RGB 0..1 floats [1,3,224,224]; normalisation and the calibrated temperature are baked in,
so it outputs logits already scaled. In the app: probabilities = softmax(output).
"""
import csv, json, os, random, sys, time
sys.path.insert(0, "/mnt/d/OCDD")
import numpy as np
import torch, torch.nn as nn
import torchvision.models as models
from torchvision import transforms as T
from PIL import Image
from train import unify, cpath          # same label merging + cache naming as training

ROOT = "/mnt/d/OCDD"
RUN = os.path.join(ROOT, "training", os.environ.get("OCDD_RUN", "run1"))
OUT = os.path.join(ROOT, os.environ.get("OCDD_OUT", "export")); os.makedirs(OUT, exist_ok=True)
CACHE_WIN = r"D:\OCDD\data\interim\cache256"          # cache names were hashed from Windows paths


def cache_file(path):
    return cpath(CACHE_WIN, path).replace("\\", "/").replace("D:/", "/mnt/d/")


ck = torch.load(os.path.join(RUN, "best.pt"), map_location="cpu")
classes = ck["classes"]
temperature = json.load(open(os.path.join(RUN, "temperature.json")))["temperature"]
cidx = {c: i for i, c in enumerate(classes)}

base = models.mobilenet_v3_large(weights=None)
base.classifier[3] = nn.Linear(base.classifier[3].in_features, len(classes))
base.load_state_dict(ck["model"]); base.eval()


class Deploy(nn.Module):
    def __init__(self, m, t):
        super().__init__()
        self.m = m
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))
        self.t = float(t)
    def forward(self, x):
        return self.m((x - self.mean) / self.std) / self.t


model = Deploy(base, temperature).eval()

# ---- images for calibration (val) and for checking accuracy (test) ----
rows = []
with open(os.path.join(ROOT, "data", os.environ.get("OCDD_INTERIM", "interim"), "manifest.csv"), newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        c = unify(r["crop"], r["label"])
        if c is not None and r["split"] in ("val", "test"):
            r["y"] = cidx[c]; rows.append(r)
random.seed(0)
val = [r for r in rows if r["split"] == "val"]; test = [r for r in rows if r["split"] == "test"]
random.shuffle(val); random.shuffle(test)
tf = T.Compose([T.Resize(256), T.CenterCrop(224), T.ToTensor()])      # 0..1, no normalisation (baked in)


def load(r):
    return tf(Image.open(cache_file(r["path"])).convert("RGB")).unsqueeze(0)


cal = [load(r) for r in val[:1000]]
chk = [(load(r), r["y"]) for r in test[:1000]]
print("calibration images: %d, check images: %d" % (len(cal), len(chk)))

# ---- ExecuTorch export ----
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import XNNPACKQuantizer, get_symmetric_quantization_config
from executorch.backends.xnnpack.utils.configs import get_transform_passes
from executorch.exir import to_edge_transform_and_lower
from torchao.quantization.pt2e.quantize_pt2e import convert_pt2e, prepare_pt2e

sample = (torch.randn(1, 3, 224, 224),)

print("exporting fp32 ...")
fp = to_edge_transform_and_lower(torch.export.export(model, sample), partitioner=[XnnpackPartitioner()]).to_executorch()
open(os.path.join(OUT, "vision_fp32.pte"), "wb").write(fp.buffer)

print("quantising to int8 (calibrating on %d photos) ..." % len(cal))
q = XNNPACKQuantizer(); q.set_global(get_symmetric_quantization_config(is_per_channel=True))
prepared = prepare_pt2e(torch.export.export(model, sample).module(), q)
with torch.no_grad():
    for x in cal:
        prepared(x)
qm = convert_pt2e(prepared)
i8 = to_edge_transform_and_lower(torch.export.export(qm, sample), partitioner=[XnnpackPartitioner()],
                                 transform_passes=get_transform_passes()).to_executorch()
open(os.path.join(OUT, "vision_int8.pte"), "wb").write(i8.buffer)
json.dump(classes, open(os.path.join(OUT, "classes.json"), "w"), indent=1)

# ---- check accuracy and speed of what was exported ----
from executorch.runtime import Runtime
rt = Runtime.get()
report = []
def say(s):
    print(s); report.append(s)
for name in ("vision_fp32.pte", "vision_int8.pte"):
    path = os.path.join(OUT, name)
    meth = rt.load_program(path).load_method("forward")
    ok = 0; t0 = time.time()
    for x, y in chk:
        out = meth.execute([x])[0]
        ok += int(out.argmax().item() == y)
    ms = 1000 * (time.time() - t0) / len(chk)
    say("%-16s %6.1f MB   accuracy on %d test photos: %.3f   (%.1f ms/photo on this PC)" % (
        name, os.path.getsize(path) / 1e6, len(chk), ok / len(chk), ms))
with torch.no_grad():
    ok = sum(int(model(x).argmax().item() == y) for x, y in chk)
say("original PyTorch model: accuracy %.3f" % (ok / len(chk)))
open(os.path.join(OUT, "export_report.txt"), "w").write("\n".join(report))
print("done. files are in D:\\OCDD\\export")
