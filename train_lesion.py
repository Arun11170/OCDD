"""Lesion finder: marks ONLY the diseased/damaged parts of a leaf. Run in Windows PowerShell (uses the RTX 3050):

    cd D:\\OCDD
    python get_data.py plantseg          # once (1.7 GB)
    python train_lesion.py --smoke       # 2-3 minutes: checks that everything works
    python train_lesion.py               # full run, about 2-3 hours; pause/resume safe (see ctl.py)
    python train_lesion.py --eval-only [--ckpt=best_v1.pt]   # score a saved model on held-out photos (a few minutes)

Training photos: PlantSeg (CC BY 4.0), 11k in-the-wild photos where people outlined each lesion; healthy photos with empty
masks teach it to leave healthy leaves alone. Model: DeepLabV3 + MobileNetV3-Large (ImageNet-pretrained backbone).
Output: D:\\OCDD\\export_lesion\\lesionseg.onnx  (input float RGB 0..1 [1,3,512,512], output lesion probability [1,1,512,512]),
        report.txt, check\\*.jpg (your testphotos with the lesion map)
In the app: damage % = lesion area inside the leaf / leaf area.
"""
import glob, hashlib, json, os, random, sys, time
import numpy as np
import torch, torch.nn as nn, torch.nn.functional as F
from PIL import Image, ImageEnhance, ImageFilter

ROOT = "D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD"
if os.environ.get("OCDD_ROOT"): ROOT = os.environ["OCDD_ROOT"]
DATA = ROOT + "/data/raw/plantseg"
OUT = ROOT + "/export_lesion" + ("_smoke" if ("--smoke" in sys.argv or "--demo" in sys.argv) else ""); os.makedirs(OUT + "/check", exist_ok=True)   # smoke/demo never touch the real files
S = 512
SMOKE = "--smoke" in sys.argv
DEMO = "--demo" in sys.argv          # synthetic data, only to test this script
EPOCHS = 1 if SMOKE else int(next((a.split("=")[1] for a in sys.argv if a.startswith("--epochs=")), 24))
EVAL_ONLY = "--eval-only" in sys.argv
CKPT = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--ckpt=")), "best.pt")
INIT = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--init=")), "")      # start from an earlier model (faster than from scratch)
FRESH = "--fresh" in sys.argv
BATCH = 4 if SMOKE else int(next((a.split("=")[1] for a in sys.argv if a.startswith("--batch=")), 8))
GREY = (124, 116, 104)
IMG_EXT = (".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG")


def find_pairs():
    """Returns (train, val) lists of (image_path, mask_path or None). Healthy photos have no mask."""
    imgs, anns = {}, {}
    for p in glob.glob(DATA + "/**/*", recursive=True):
        if not p.endswith(IMG_EXT): continue
        low = p.replace("\\", "/").lower()
        stem = os.path.splitext(os.path.basename(p))[0]
        if "/annotations/" in low or "/masks/" in low or "/labels/" in low:
            anns.setdefault(stem, p)
        elif "/json/" in low:
            continue
        else:
            imgs.setdefault(stem, p)
    pairs = []
    for stem, p in imgs.items():
        if stem in anns: pairs.append((p, anns[stem]))
        elif "health" in p.replace("\\", "/").lower(): pairs.append((p, None))
    tr, va = [], []
    for p, m in pairs:
        low = p.replace("\\", "/").lower()
        if "/test/" in low or "/val/" in low: va.append((p, m))
        elif "/train/" in low: tr.append((p, m))
        else: (va if int(hashlib.md5(p.encode()).hexdigest(), 16) % 10 == 0 else tr).append((p, m))
    h_tr, h_va = healthy_pairs()
    return tr + h_tr, va + h_va


def healthy_pairs(n_train=2200, n_val=300, n_lab=300):
    """Healthy leaves with an EMPTY mask. PlantSeg only has diseased photos, so without these the model learns
    'every leaf has lesions' and paints healthy leaves red. Taken from the field datasets in the training list (+ a few lab leaves)."""
    import csv
    mf = None
    for name in ("interim_v3", "interim_v2"):
        p = ROOT + "/data/%s/manifest.csv" % name
        if os.path.isfile(p): mf = p; break
    if mf is None: return [], []
    rnd = random.Random(5); by = {"train": {}, "val": {}}; lab = []
    with open(mf, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            lbl = r["label"].lower()
            if r["crop"] == "pest" or not ("healthy" in lbl or lbl == "plantdoc_tomato_leaf"): continue
            if r["split"] not in ("train", "val"): continue
            path = r["path"]
            if os.name != "nt": path = path.replace("D:\\OCDD", ROOT).replace("\\", "/")
            if not os.path.isfile(path): continue
            if r["domain"] == "field": by[r["split"]].setdefault(r["crop"], []).append((path, None))
            elif r["split"] == "train": lab.append((path, None))
    def spread(d, n):                                   # the same number per crop, taken round robin
        for v in d.values(): rnd.shuffle(v)
        out, i = [], 0
        while len(out) < n and any(i < len(v) for v in d.values()):
            out += [v[i] for v in d.values() if i < len(v)]; i += 1
        return out[:n]
    rnd.shuffle(lab)
    return spread(by["train"], n_train) + lab[:n_lab], spread(by["val"], n_val)


def demo_pairs():
    d = OUT + "/demo"; os.makedirs(d, exist_ok=True)
    rnd = np.random.RandomState(0); out = []
    for i in range(60):
        im = np.zeros((300, 400, 3), np.uint8); im[:] = (40, 140 + rnd.randint(0, 40), 50)
        m = np.zeros((300, 400), np.uint8)
        if i % 5:
            for _ in range(rnd.randint(2, 7)):
                x, y, r = rnd.randint(30, 370), rnd.randint(30, 270), rnd.randint(8, 25)
                yy, xx = np.ogrid[:300, :400]; blob = (xx - x) ** 2 + (yy - y) ** 2 < r * r
                im[blob] = (120, 80, 40); m[blob] = 1
        a, b = "%s/d%d.jpg" % (d, i), "%s/d%d.png" % (d, i)
        Image.fromarray(im).save(a); Image.fromarray(m).save(b); out.append((a, b))
    return out[:48], out[48:]


def letterbox(im, mask=None, size=S):
    w, h = im.size; k = size / max(w, h)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    r = im.resize((nw, nh), Image.BILINEAR)
    sq = Image.new("RGB", (size, size), GREY); ox, oy = (size - nw) // 2, (size - nh) // 2; sq.paste(r, (ox, oy))
    mm = None
    if mask is not None:
        mr = mask.resize((nw, nh), Image.NEAREST); mm = Image.new("L", (size, size), 0); mm.paste(mr, (ox, oy))
    return sq, mm


class DS(torch.utils.data.Dataset):
    def __init__(self, pairs, train): self.pairs, self.train = pairs, train
    def __len__(self): return len(self.pairs)
    def __getitem__(self, i):
        ip, mp = self.pairs[i]
        im = Image.open(ip)
        try: im.draft("RGB", (1100, 1100))                       # JPEG: decode at reduced size when the photo is huge (much faster)
        except Exception: pass
        im = im.convert("RGB")
        m = Image.open(mp) if mp else Image.new("L", im.size, 0)
        m = Image.fromarray(((np.asarray(m.convert("L") if m.mode != "L" else m) > 0) * 255).astype(np.uint8))
        if m.size != im.size: m = m.resize(im.size, Image.NEAREST)
        if self.train:
            k = random.uniform(0.8, 1.5) * 512 / max(im.size)           # lesions are small: train a little larger than the app uses
            im = im.resize((max(8, round(im.size[0] * k)), max(8, round(im.size[1] * k))), Image.BILINEAR); m = m.resize(im.size, Image.NEAREST)
            w, h = im.size
            if w < S or h < S:
                pad = Image.new("RGB", (max(w, S), max(h, S)), GREY); pad.paste(im, (0, 0)); im = pad
                pm = Image.new("L", im.size, 0); pm.paste(m, (0, 0)); m = pm
                w, h = im.size
            x0, y0 = random.randint(0, w - S), random.randint(0, h - S)
            im = im.crop((x0, y0, x0 + S, y0 + S)); m = m.crop((x0, y0, x0 + S, y0 + S))
            if random.random() < 0.5: im = im.transpose(Image.FLIP_LEFT_RIGHT); m = m.transpose(Image.FLIP_LEFT_RIGHT)
            if random.random() < 0.3: im = im.transpose(Image.FLIP_TOP_BOTTOM); m = m.transpose(Image.FLIP_TOP_BOTTOM)
            im = ImageEnhance.Brightness(im).enhance(random.uniform(0.7, 1.3)); im = ImageEnhance.Contrast(im).enhance(random.uniform(0.75, 1.3))
            im = ImageEnhance.Color(im).enhance(random.uniform(0.7, 1.3))
            if random.random() < 0.25: im = im.filter(ImageFilter.GaussianBlur(random.uniform(0.5, 1.6)))
        else:
            im, m = letterbox(im, m)
        x = torch.from_numpy(np.asarray(im, np.float32) / 255.0).permute(2, 0, 1)
        y = torch.from_numpy((np.asarray(m) > 127).astype(np.float32))[None]
        return x, y


class Deploy(nn.Module):
    def __init__(self, m):
        super().__init__(); self.m = m
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))
    def forward(self, x):
        return torch.sigmoid(self.m((x - self.mean) / self.std)["out"])


def band(f):                     # severity band from the lesion share of the photo
    return 0 if f < 0.05 else (1 if f < 0.20 else 2)


def ranks(v):
    o = np.argsort(np.argsort(v)); return o.astype(np.float64)


def evaluate_full(net, dep, dv, dev):
    """Honest checks on held-out photos the model never trained on."""
    net.eval(); ious, tf_, pf_, hf = [], [], [], []
    with torch.no_grad():
        for x, y in dv:
            pr = dep(x.to(dev)).cpu() > 0.5; yt = y > 0.5
            for a, b in zip(pr, yt):
                if b.sum() == 0: hf.append(a.float().mean().item())
                else:
                    ious.append(((a & b).sum() / ((a | b).sum() + 1e-6)).item())
                    tf_.append(b.float().mean().item()); pf_.append(a.float().mean().item())
    net.train()
    r = {"iou": float(np.mean(ious)) if ious else 0.0, "n_lesion": len(ious), "n_healthy": len(hf),
         "healthy_area": float(np.mean(hf)) if hf else 0.0,
         "healthy_alarm": float(np.mean([h > 0.01 for h in hf])) if hf else 0.0}
    if len(tf_) > 2:
        r["spearman"] = float(np.corrcoef(ranks(np.array(tf_)), ranks(np.array(pf_)))[0, 1])
        r["band_agree"] = float(np.mean([band(t) == band(p) for t, p in zip(tf_, pf_)]))
        r["band_within_one"] = float(np.mean([abs(band(t) - band(p)) <= 1 for t, p in zip(tf_, pf_)]))
    return r


def format_eval(r):
    L = ["lesion overlap (IoU) on %d held-out photos with human-outlined lesions: %.3f   (target >= 0.50)" % (r["n_lesion"], r["iou"])]
    if "spearman" in r:
        L.append("does a worse leaf get a bigger number? rank correlation of predicted vs outlined damage: %.3f   (target >= 0.80)" % r["spearman"])
        L.append("severity band (Low <5%% / Moderate 5-20%% / Severe >20%% of the photo) exactly right: %.0f%%   (target >= 80%%)   within one band: %.0f%%" % (100 * r["band_agree"], 100 * r["band_within_one"]))
    L.append("%d HEALTHY field photos: share of photos where >1%% of the picture is wrongly flagged: %.1f%%   (target <= 5%%);  average flagged area %.2f%%   (target <= 1%%)" % (r["n_healthy"], 100 * r["healthy_alarm"], 100 * r["healthy_area"]))
    return L


def overlay_testphotos(sess_run, folder, report):
    tp = ROOT + "/testphotos"
    if not os.path.isdir(tp): return
    os.makedirs(folder, exist_ok=True)
    report += ["", "lesion area as % of the photo for your testphotos (red = lesion found):"]
    for f in sorted(os.listdir(tp)):
        if not f.lower().endswith((".jpg", ".jpeg", ".png")): continue
        im = Image.open(os.path.join(tp, f)).convert("RGB"); sq, _ = letterbox(im)
        p = sess_run((np.asarray(sq, np.float32) / 255.0).transpose(2, 0, 1)[None])
        a = np.asarray(sq, np.float32).copy(); a[p > 0.5] = 0.35 * a[p > 0.5] + 0.65 * np.array([255, 0, 0])
        Image.fromarray(np.hstack([np.asarray(sq), a.astype(np.uint8)])).save(folder + "/" + os.path.splitext(f)[0] + ".jpg", quality=80)
        report.append("%-48s lesion %5.1f %% of photo" % (f[:48], 100 * float((p > 0.5).mean())))


def main():
    from torchvision.models.segmentation import deeplabv3_mobilenet_v3_large
    from torchvision.models import MobileNet_V3_Large_Weights
    from ctl import lower_priority, pause_requested, exit_paused, atomic_save
    lower_priority()
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    if dev == "cuda": torch.backends.cudnn.benchmark = True
    print("device:", dev, "smoke:", SMOKE, "demo:", DEMO)
    tr, va = demo_pairs() if DEMO else find_pairs()
    if not tr:
        print("folders found under", DATA, ":", [os.path.basename(p) for p in glob.glob(DATA + "/*")][:20])
        sys.exit("No PlantSeg photos with masks found under %s. Run: python get_data.py plantseg, then python plantseg_overview.py and send me the output." % DATA)
    if SMOKE: tr, va = tr[:40], va[:16]
    print("training photos %d (with lesions %d, healthy %d), validation %d (healthy %d)" % (
        len(tr), sum(1 for p in tr if p[1]), sum(1 for p in tr if not p[1]), len(va), sum(1 for p in va if not p[1])))
    from progress import Beat, auto_workers
    nw = 0 if (SMOKE or DEMO or EVAL_ONLY) else auto_workers(cap=8, per_worker_gb=0.8)
    dl = torch.utils.data.DataLoader(DS(tr, True), batch_size=BATCH, shuffle=True, num_workers=nw, drop_last=True, persistent_workers=nw > 0)
    dv = torch.utils.data.DataLoader(DS(va, False), batch_size=4, num_workers=min(nw, 3), persistent_workers=False)   # short-lived workers: faster checks without holding memory
    net = deeplabv3_mobilenet_v3_large(weights=None, weights_backbone=None if (DEMO or EVAL_ONLY) else MobileNet_V3_Large_Weights.IMAGENET1K_V1, num_classes=1).to(dev)
    dep = Deploy(net).to(dev)

    if EVAL_ONLY:
        net.load_state_dict(torch.load(os.path.join(OUT, CKPT), map_location=dev)); net.eval()
        r = evaluate_full(net, dep, dv, dev)
        rep = ["EVALUATION of %s" % CKPT] + format_eval(r)
        net.eval(); 
        def run(x):
            with torch.no_grad(): return dep(torch.from_numpy(x).to(dev)).cpu().numpy()[0, 0]
        overlay_testphotos(run, OUT + "/check_eval", rep)
        open(OUT + "/eval_%s.txt" % os.path.splitext(CKPT)[0], "w").write("\n".join(rep)); print("\n".join(rep)); return

    if INIT and os.path.isfile(os.path.join(OUT, INIT)):
        net.load_state_dict(torch.load(os.path.join(OUT, INIT), map_location=dev)); print("starting from", INIT, flush=True)
    opt = torch.optim.AdamW(net.parameters(), lr=1e-3, weight_decay=1e-4)
    total = EPOCHS * len(dl)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=1e-3, total_steps=max(total, 10), pct_start=0.1 if total >= 100 else 0.4)   # tiny smoke runs need a longer warm-up fraction
    scaler = torch.amp.GradScaler("cuda", enabled=(dev == "cuda"))

    def loss_fn(lg, y):
        bce = F.binary_cross_entropy_with_logits(lg, y, pos_weight=torch.tensor(3.0, device=lg.device))
        p = torch.sigmoid(lg); inter = (p * y).sum((1, 2, 3)); den = p.sum((1, 2, 3)) + y.sum((1, 2, 3))
        has = (y.sum((1, 2, 3)) > 0).float()                   # dice only where there is something to find
        dice = ((1 - (2 * inter + 1) / (den + 1)) * has).sum() / has.sum().clamp(min=1)
        healthy_pen = (torch.sigmoid(lg) * (1 - has).view(-1, 1, 1, 1)).mean() * 2.0   # healthy photo: anything flagged costs extra
        return bce + dice + healthy_pen

    best, start_ep = -9.0, 0
    last = os.path.join(OUT, "last.pt")
    sig = {"epochs": EPOCHS, "batch": BATCH, "n": len(tr), "smoke": SMOKE}
    if os.path.isfile(last) and not FRESH and not SMOKE:
        ck = torch.load(last, map_location=dev, weights_only=False)
        if ck.get("sig") == sig:
            net.load_state_dict(ck["net"]); opt.load_state_dict(ck["opt"]); sched.load_state_dict(ck["sched"]); scaler.load_state_dict(ck["scaler"])
            start_ep, best = ck["ep"], ck["best"]
            print("RESUMING lesion finder from epoch %d of %d" % (start_ep + 1, EPOCHS), flush=True)
        else:
            print("saved last.pt belongs to a different setup: starting fresh", flush=True)
    t0 = time.time(); net.train()
    beat = Beat('lesion', EPOCHS, start_ep=start_ep)
    for ep in range(start_ep, EPOCHS):
        tot = n = 0
        for bi, (x, y) in enumerate(dl):
            x, y = x.to(dev), y.to(dev)
            xin = (x - dep.mean) / dep.std
            with torch.autocast(device_type="cuda", dtype=torch.float16, enabled=(dev == "cuda")):
                out = net(xin)["out"]
            loss = loss_fn(out.float(), y)
            opt.zero_grad(set_to_none=True); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step()
            tot += loss.item(); n += 1
            beat.tick(ep, bi, len(dl), tot / n)
        r = evaluate_full(net, dep, dv, dev)
        print("epoch %2d/%d  loss %.3f  val lesion IoU %.3f  healthy photos wrongly flagged %.1f%% (avg area %.2f%%)  (%.0fs)" % (
            ep + 1, EPOCHS, tot / max(n, 1), r["iou"], 100 * r["healthy_alarm"], 100 * r["healthy_area"], time.time() - t0), flush=True)
        score = r["iou"] - 3 * r["healthy_area"] - 0.5 * r["healthy_alarm"]
        if score > best:
            best = score; atomic_save(net.state_dict(), OUT + "/best.pt")
        if not SMOKE:
            atomic_save({"net": net.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(), "scaler": scaler.state_dict(),
                         "ep": ep + 1, "best": best, "sig": sig}, last)
        if pause_requested() and ep + 1 < EPOCHS:
            exit_paused("epoch %d of %d of the lesion finder" % (ep + 1, EPOCHS))
    net.load_state_dict(torch.load(OUT + "/best.pt", map_location=dev)); net.eval()
    r = evaluate_full(net, dep, dv, dev)
    report = ["lesion finder v2 (held-out PlantSeg photos + healthy field photos)"] + format_eval(r)
    print("\n".join(report))

    # ---- export ----
    net.cpu().eval(); dep = Deploy(net).eval()
    path = OUT + "/lesionseg.onnx"
    try: torch.onnx.export(dep, torch.zeros(1, 3, S, S), path, input_names=["image"], output_names=["lesion"], opset_version=17, dynamo=False)
    except TypeError: torch.onnx.export(dep, torch.zeros(1, 3, S, S), path, input_names=["image"], output_names=["lesion"], opset_version=17)
    report.append("lesionseg.onnx %.1f MB" % (os.path.getsize(path) / 1e6)); print(report[-1])

    # ---- try on your test photos (lesion map only; damage % uses the leaf finder in the app) ----
    try:
        import onnxruntime as ort
        sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        overlay_testphotos(lambda x: sess.run(None, {"image": x})[0][0, 0], OUT + "/check", report)
    except Exception as e:
        report.append("test photos skipped: %s" % e)
    open(OUT + "/report.txt", "w").write("\n".join(report)); print("\n".join(report[-40:])); print("done. see", OUT)
    open(OUT + "/DONE_v2", "w").write("ok")


if __name__ == "__main__":
    main()
