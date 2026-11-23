"""Leaf finder for the damage % measurement. Run INSIDE WSL:

  Windows PowerShell (uses the RTX 3050 - the WSL Python here is CPU-only and far too slow):
    cd D:\\OCDD
    pip install onnx onnxruntime onnxscript
    python train_leafseg.py --smoke      # 1-2 minutes, checks everything works
    python train_leafseg.py              # full run

Why: the old method guessed the leaf outline from "green" pixels, so soil between leaves and shadows were counted as
damage. This trains a small network (MobileNetV3 + LR-ASPP, ~3 M parameters) that answers one question per pixel:
"is this leaf?". Training pictures: PlantVillage leaves with their ready-made background-free masks, pasted onto
real field backgrounds (soil, straw, hands, sky cut from our field datasets) with random size, rotation, shadows,
blur and colour changes. The damage itself is then measured inside the leaf with a colour rule that was tuned on
PlantVillage (healthy leaves ~0.6 % median, diseased ~18 % mean).

Output in D:\\OCDD\\export_seg\\ : leafseg.onnx  (input float RGB 0..1 [1,3,320,320], output leaf probability [1,1,320,320])
                                  report.txt, check\\*.jpg (your testphotos with leaf outline and measured damage)
"""
import csv, json, math, os, random, sys, time, hashlib
import numpy as np
import torch, torch.nn as nn, torch.nn.functional as F
from PIL import Image, ImageFilter, ImageEnhance
from torchvision.models.segmentation import lraspp_mobilenet_v3_large
from torchvision.models import MobileNet_V3_Large_Weights

ROOT = "D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD"      # runs in Windows Python (GPU) or WSL
PV = ROOT + "/data/raw/plantvillage_all/plantvillage dataset"
OUT = ROOT + "/export_seg"; os.makedirs(OUT + "/check", exist_ok=True)
S = 320
SMOKE = "--smoke" in sys.argv
ITERS = 40 if SMOKE else 5000
BATCH = 8 if SMOKE else 16
NW = 0 if SMOKE else max(2, min(8, (os.cpu_count() or 4) - 3))
dev = "cuda" if torch.cuda.is_available() else "cpu"
print("device:", dev, "smoke:", SMOKE)


# ---------------------------------------------------------------- leaf bank (PlantVillage colour + segmented mask)
def leaf_pairs():
    pairs = []
    seg = os.path.join(PV, "segmented")
    for cls in sorted(os.listdir(seg)):
        for f in os.listdir(os.path.join(seg, cls)):
            stem = f[:-len("_final_masked.jpg")] if f.endswith("_final_masked.jpg") else None
            if stem is None: continue
            pairs.append((os.path.join(PV, "color", cls, stem + ".JPG"), os.path.join(seg, cls, f)))
    pairs = [p for p in pairs if os.path.exists(p[0])]
    return pairs


pairs = leaf_pairs()
def is_val(p): return int(hashlib.md5(p[0].encode()).hexdigest(), 16) % 20 == 0
train_pairs = [p for p in pairs if not is_val(p)]; val_pairs = [p for p in pairs if is_val(p)]
if SMOKE:
    random.Random(0).shuffle(train_pairs); train_pairs = train_pairs[:300]; val_pairs = val_pairs[:40]
print("leaf pictures: train %d, val %d" % (len(train_pairs), len(val_pairs)))


# ---------------------------------------------------------------- background bank (real field pictures without much green)
def build_bank():
    bank_file = OUT + "/bgbank.npy"
    if os.path.exists(bank_file) and not SMOKE:
        return np.load(bank_file)
    sys.path.insert(0, ROOT)
    from train import cpath
    cache = r"D:\OCDD\data\interim\cache256"
    rows = []
    with open(ROOT + "/data/interim_v2/manifest.csv", newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["domain"] == "field" and r["crop"] != "pest" and r["split"] == "train": rows.append(r)
    rnd = random.Random(1); rnd.shuffle(rows)
    want = 300 if SMOKE else 3000
    crops = []
    for r in rows:
        if len(crops) >= want: break
        p = cpath(cache, r["path"])
        if os.name != "nt": p = p.replace("\\", "/").replace("D:/", "/mnt/d/")
        try: im = Image.open(p).convert("RGB")
        except Exception: continue
        a = np.asarray(im)
        for _ in range(3):
            sz = rnd.choice((64, 96, 128)); 
            if a.shape[0] < sz or a.shape[1] < sz: continue
            y = rnd.randint(0, a.shape[0] - sz); x = rnd.randint(0, a.shape[1] - sz)
            c = a[y:y + sz, x:x + sz]
            r_, g_, b_ = [c[..., i].astype(int) for i in range(3)]
            green = ((2 * g_ - r_ - b_) > 18).mean()
            if green < 0.05:
                crops.append(np.asarray(Image.fromarray(c).resize((128, 128), Image.BILINEAR)))
    bank = np.stack(crops) if crops else np.zeros((1, 128, 128, 3), np.uint8)
    if not SMOKE: np.save(bank_file, bank)
    print("background crops:", len(bank))
    return bank


BANK = build_bank()
print("background bank:", BANK.shape)


# ---------------------------------------------------------------- composing a training picture
def earthy(rnd):
    import colorsys
    h = rnd.uniform(0.0, 0.12); sa = rnd.uniform(0.35, 0.85); v = rnd.uniform(0.35, 0.9)
    base = np.array(colorsys.hsv_to_rgb(h, sa, v)) * 255
    a = np.broadcast_to(base, (S, S, 3)).astype(np.float32)
    noise = np.asarray(Image.fromarray((np.random.rand(S // 8, S // 8) * 255).astype(np.uint8)).resize((S, S), Image.BICUBIC), np.float32)[..., None] / 255.0
    a = a * (0.8 + 0.4 * noise) + np.random.randn(S, S, 3) * rnd.uniform(2, 14)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def make_bg(rnd):
    t = rnd.random()
    if t < 0.25: return earthy(rnd)
    t = (t - 0.25) / 0.75
    if t < 0.75:
        tiles = []
        k = rnd.choice((1, 1, 2, 3))                      # mosaic of crops, big or small
        base = np.zeros((S, S, 3), np.uint8)
        n = k
        cell = S // n
        for i in range(n):
            for j in range(n):
                c = BANK[rnd.randrange(len(BANK))]
                c = Image.fromarray(c).resize((cell + (S - cell * n if i == n - 1 else 0), cell + (S - cell * n if j == n - 1 else 0)), Image.BILINEAR)
                if rnd.random() < 0.5: c = c.transpose(Image.FLIP_LEFT_RIGHT)
                base[i * cell:i * cell + c.size[1], j * cell:j * cell + c.size[0]] = np.asarray(c)
        return Image.fromarray(base)
    if t < 0.9:
        col0 = np.array([rnd.randint(30, 220) for _ in range(3)], float); col1 = np.array([rnd.randint(30, 220) for _ in range(3)], float)
        g = np.linspace(0, 1, S)[None, :, None] if rnd.random() < 0.5 else np.linspace(0, 1, S)[:, None, None]
        a = col0 * (1 - g) + col1 * g
        a = np.broadcast_to(a, (S, S, 3)) + np.random.randn(S, S, 3) * rnd.uniform(0, 12)
        return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    return Image.fromarray(np.full((S, S, 3), rnd.randint(60, 230), np.uint8)).filter(ImageFilter.GaussianBlur(1))


def cutout(pair, rnd):
    im = Image.open(pair[0]).convert("RGB"); m = Image.open(pair[1]).convert("L")
    a = np.asarray(m) > 30
    ys, xs = np.where(a)
    if len(ys) < 200: return None
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    im = im.crop(box); alpha = Image.fromarray((a[box[1]:box[3], box[0]:box[2]] * 255).astype(np.uint8))
    return im, alpha


def blurred_leaves(canvas, pairs_, rnd):
    """Out-of-focus leaves behind the subject: they look like leaves but are NOT the leaf being photographed (mask stays 0)."""
    for _ in range(rnd.randint(2, 7)):
        c = cutout(rnd.choice(pairs_), rnd)
        if c is None: continue
        im, al = c
        f = S * rnd.uniform(0.2, 0.7) / max(im.size)
        im = im.resize((max(8, int(im.size[0] * f)), max(8, int(im.size[1] * f))), Image.BILINEAR); al = al.resize(im.size, Image.BILINEAR)
        ang = rnd.uniform(0, 360)
        im = im.rotate(ang, expand=True, resample=Image.BILINEAR); al = al.rotate(ang, expand=True, resample=Image.BILINEAR)
        im = ImageEnhance.Brightness(im).enhance(rnd.uniform(0.6, 1.3))
        r = rnd.uniform(3, 9)
        pad = int(r * 3)
        im2 = Image.new("RGB", (im.size[0] + 2 * pad, im.size[1] + 2 * pad), tuple(int(v) for v in np.asarray(im).reshape(-1, 3).mean(0)))
        im2.paste(im, (pad, pad)); al2 = Image.new("L", im2.size, 0); al2.paste(al, (pad, pad))
        im2 = im2.filter(ImageFilter.GaussianBlur(r)); al2 = al2.filter(ImageFilter.GaussianBlur(r))
        w, h = im2.size
        x0 = rnd.randint(-w // 3, S - w + w // 3) if w < S else rnd.randint(S - w, 0)
        y0 = rnd.randint(-h // 3, S - h + h // 3) if h < S else rnd.randint(S - h, 0)
        xs0, ys0 = max(x0, 0), max(y0, 0); xs1, ys1 = min(x0 + w, S), min(y0 + h, S)
        if xs1 <= xs0 or ys1 <= ys0: continue
        a = np.asarray(al2, np.float32)[ys0 - y0:ys1 - y0, xs0 - x0:xs1 - x0, None] / 255.0
        canvas[ys0:ys1, xs0:xs1] = canvas[ys0:ys1, xs0:xs1] * (1 - a) + np.asarray(im2, np.float32)[ys0 - y0:ys1 - y0, xs0 - x0:xs1 - x0] * a
    return canvas


def compose(pairs_, rnd, allow_empty=True):
    bg = make_bg(rnd); canvas = np.asarray(bg).astype(np.float32); mask = np.zeros((S, S), np.float32)
    if rnd.random() < 0.45: canvas = blurred_leaves(canvas, pairs_, rnd)
    if allow_empty and rnd.random() < 0.15:
        return canvas, mask
    dense = rnd.random() < 0.3
    for k in range(rnd.randint(6, 14) if dense else rnd.choice((1, 1, 1, 2, 3))):
        c = cutout(rnd.choice(pairs_), rnd)
        if c is None: continue
        im, al = c
        scale = rnd.uniform(0.12, 0.3) if dense else (rnd.uniform(0.3, 1.0) if k == 0 else rnd.uniform(0.25, 0.7))
        long = max(im.size); f = S * scale / long
        im = im.resize((max(8, int(im.size[0] * f)), max(8, int(im.size[1] * f))), Image.BILINEAR)
        al = al.resize(im.size, Image.BILINEAR)
        if rnd.random() < 0.5: im = im.transpose(Image.FLIP_LEFT_RIGHT); al = al.transpose(Image.FLIP_LEFT_RIGHT)
        ang = rnd.uniform(0, 360)
        im = im.rotate(ang, expand=True, resample=Image.BILINEAR); al = al.rotate(ang, expand=True, resample=Image.BILINEAR)
        # leaf lighting: brightness and a soft gradient
        e = ImageEnhance.Brightness(im).enhance(rnd.uniform(0.7, 1.25)); im = ImageEnhance.Color(e).enhance(rnd.uniform(0.7, 1.2))
        w, h = im.size
        x0 = rnd.randint(-w // 4, S - w + w // 4) if w < S else rnd.randint(S - w, 0)
        y0 = rnd.randint(-h // 4, S - h + h // 4) if h < S else rnd.randint(S - h, 0)
        # canvas-sized layers
        layer = np.zeros((S, S, 3), np.float32); amask = np.zeros((S, S), np.float32)
        xs0, ys0 = max(x0, 0), max(y0, 0); xs1, ys1 = min(x0 + w, S), min(y0 + h, S)
        if xs1 <= xs0 or ys1 <= ys0: continue
        layer[ys0:ys1, xs0:xs1] = np.asarray(im, np.float32)[ys0 - y0:ys1 - y0, xs0 - x0:xs1 - x0]
        amask[ys0:ys1, xs0:xs1] = np.asarray(al, np.float32)[ys0 - y0:ys1 - y0, xs0 - x0:xs1 - x0] / 255.0
        if rnd.random() < 0.7:                                   # soft drop shadow on the ground
            sh = Image.fromarray((amask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(rnd.uniform(3, 9)))
            sh = np.roll(np.asarray(sh, np.float32) / 255.0, (rnd.randint(2, 14), rnd.randint(-10, 10)), (0, 1))
            canvas *= (1 - 0.45 * rnd.random() * sh)[..., None]
        canvas = canvas * (1 - amask[..., None]) + layer * amask[..., None]
        mask = np.maximum(mask, (amask > 0.5).astype(np.float32))
    if rnd.random() < 0.3:                                       # a hand / finger: not a leaf
        from PIL import ImageDraw
        sk = Image.new("L", (S, S), 0); d = ImageDraw.Draw(sk)
        x, y = rnd.randint(0, S), rnd.randint(0, S); ang = rnd.uniform(0, 6.28)
        for t_ in range(rnd.randint(1, 4)):
            ln, wd = rnd.randint(60, 180), rnd.randint(14, 40); a_ = ang + rnd.uniform(-0.4, 0.4)
            d.line([(x, y), (x + ln * math.cos(a_), y + ln * math.sin(a_))], fill=255, width=wd)
        d.ellipse([x - 40, y - 40, x + 40, y + 40], fill=255)
        sk = np.asarray(sk.filter(ImageFilter.GaussianBlur(1.5)), np.float32) / 255.0
        import colorsys
        col = np.array(colorsys.hsv_to_rgb(rnd.uniform(0.02, 0.09), rnd.uniform(0.25, 0.6), rnd.uniform(0.45, 0.95))) * 255
        tex = col * (0.9 + 0.2 * np.random.rand(S, S, 1))
        canvas = canvas * (1 - sk[..., None]) + tex * sk[..., None]
        mask = mask * (1 - (sk > 0.5))
    return canvas, mask


def finish(canvas, rnd):
    im = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
    im = ImageEnhance.Brightness(im).enhance(rnd.uniform(0.6, 1.3)); im = ImageEnhance.Contrast(im).enhance(rnd.uniform(0.7, 1.3))
    if rnd.random() < 0.3: im = im.filter(ImageFilter.GaussianBlur(rnd.uniform(0.5, 1.8)))
    a = np.asarray(im, np.float32)
    if rnd.random() < 0.5: a = a + np.random.randn(*a.shape) * rnd.uniform(0, 8)
    return np.clip(a, 0, 255).astype(np.uint8)


class DS(torch.utils.data.Dataset):
    def __init__(self, pairs_, n, seed=None): self.p, self.n, self.seed = pairs_, n, seed
    def __len__(self): return self.n
    def __getitem__(self, i):
        rnd = random.Random(self.seed * 100003 + i) if self.seed is not None else random.Random()
        if self.seed is not None: np.random.seed((self.seed * 100003 + i) % (2 ** 31))
        c, m = compose(self.p, rnd)
        x = torch.from_numpy(finish(c, rnd)).permute(2, 0, 1).float() / 255.0
        return x, torch.from_numpy(m).long()


# ---------------------------------------------------------------- model
class Deploy(nn.Module):
    def __init__(self, m):
        super().__init__(); self.m = m
        self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))
    def forward(self, x):
        return torch.softmax(self.m((x - self.mean) / self.std)["out"], 1)[:, 1:2]


if __name__ == "__main__":
    net = lraspp_mobilenet_v3_large(weights=None, weights_backbone=MobileNet_V3_Large_Weights.IMAGENET1K_V1, num_classes=2).to(dev)
    dep = Deploy(net).to(dev)
    opt = torch.optim.AdamW(net.parameters(), lr=1e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=1e-3, total_steps=ITERS, pct_start=0.15)
    scaler = torch.cuda.amp.GradScaler(enabled=(dev == "cuda"))
    dl = torch.utils.data.DataLoader(DS(train_pairs, ITERS * BATCH), batch_size=BATCH, num_workers=NW, shuffle=False, drop_last=True,
                                     persistent_workers=NW > 0)


    def loss_fn(logits, y):
        ce = F.cross_entropy(logits, y)
        p = torch.softmax(logits, 1)[:, 1]; t = (y == 1).float()
        dice = 1 - (2 * (p * t).sum() + 1) / (p.sum() + t.sum() + 1)
        return ce + dice


    def evaluate(n=400):
        net.eval(); ious = []; fp = []
        d = torch.utils.data.DataLoader(DS(val_pairs, 40 if SMOKE else n, seed=7), batch_size=16, num_workers=0)
        with torch.no_grad():
            for x, y in d:
                x = x.to(dev); pr = dep(x)[:, 0].cpu() > 0.5; yt = (y == 1)
                for a, b in zip(pr, yt):
                    if b.sum() == 0: fp.append(a.float().mean().item()); continue
                    ious.append(((a & b).sum() / ((a | b).sum() + 1e-6)).item())
        net.train()
        return float(np.mean(ious)), float(np.mean(fp)) if fp else 0.0


    t0 = time.time(); net.train(); it = 0
    for x, y in dl:
        x, y = x.to(dev), y.to(dev)
        # normalise like the deployed model does
        xin = (x - dep.mean) / dep.std
        with torch.autocast(device_type="cuda", dtype=torch.float16, enabled=(dev == "cuda")):
            out = net(xin)["out"]; loss = loss_fn(out.float(), y)
        opt.zero_grad(set_to_none=True); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step(); it += 1
        if it % (10 if SMOKE else 50) == 0:
            el = time.time() - t0
            print("  [leafseg] iter %d/%d (%d%%)  loss %.3f  elapsed %dm%02ds  about %dm left  (%s)" % (it, ITERS, 100 * it // ITERS, loss.item(), el // 60, el % 60, (ITERS - it) * el / it / 60, time.strftime("%H:%M:%S")), flush=True)
        if it % 1500 == 0 and not SMOKE:
            iou, fp = evaluate(200); print("  val IoU %.3f  false-leaf area on empty backgrounds %.3f" % (iou, fp), flush=True)
    iou, fp = evaluate()
    report = ["leaf finder (synthetic val, held-out PlantVillage leaves): IoU %.3f, false leaf area on empty backgrounds %.3f" % (iou, fp)]
    print(report[0])

    # ---------------------------------------------------------------- export
    net.eval().cpu(); dep = dep.cpu().eval()
    onnx_path = OUT + "/leafseg.onnx"
    try:
        torch.onnx.export(dep, torch.zeros(1, 3, S, S), onnx_path, input_names=["image"], output_names=["leaf"], opset_version=17, dynamo=False)
    except TypeError:
        torch.onnx.export(dep, torch.zeros(1, 3, S, S), onnx_path, input_names=["image"], output_names=["leaf"], opset_version=17)
    report.append("leafseg.onnx %.1f MB" % (os.path.getsize(onnx_path) / 1e6))

    # ---------------------------------------------------------------- try it on your real photos
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])


    def damage(im_rgb, leaf):
        """Colour rule tuned on PlantVillage: inside the leaf, not green (hue 35-95, sat>=30, val>=30) and not just dark green shade."""
        import colorsys
        a = np.asarray(im_rgb.convert("HSV"), np.uint8)       # PIL hue 0..255 -> degrees*255/360
        hue = a[..., 0].astype(np.float32) * 360.0 / 255.0 / 2.0   # to OpenCV scale 0..180
        sat, val = a[..., 1], a[..., 2]
        healthy = (hue >= 35) & (hue <= 95) & (sat >= 30) & (val >= 30)
        shade = (val < 45) & (hue >= 35) & (hue <= 95)
        dm = leaf & ~healthy & ~shade
        return dm


    def run_photo(path):
        im = Image.open(path).convert("RGB")
        w, h = im.size; k = 512 / max(w, h)
        big = im.resize((max(1, int(w * k)), max(1, int(h * k))), Image.BILINEAR)
        sq = Image.new("RGB", (S, S), (124, 116, 104))
        ks = S / max(big.size); sm = big.resize((max(1, int(big.size[0] * ks)), max(1, int(big.size[1] * ks))), Image.BILINEAR)
        ox, oy = (S - sm.size[0]) // 2, (S - sm.size[1]) // 2; sq.paste(sm, (ox, oy))
        x = (np.asarray(sq, np.float32) / 255.0).transpose(2, 0, 1)[None]
        p = sess.run(None, {"image": x})[0][0, 0]
        pm = Image.fromarray((p * 255).astype(np.uint8)).resize((S, S))
        pm = pm.crop((ox, oy, ox + sm.size[0], oy + sm.size[1])).resize(big.size, Image.BILINEAR)
        leaf = np.asarray(pm) > 127
        dm = damage(big, leaf)
        pct = 100.0 * dm.sum() / max(leaf.sum(), 1)
        base = np.asarray(big, np.float32)
        ov = base.copy(); ov[leaf & ~dm] = 0.65 * ov[leaf & ~dm] + 0.35 * np.array([0, 255, 0]); ov[dm] = 0.4 * ov[dm] + 0.6 * np.array([255, 0, 0])
        return pct, leaf.mean(), Image.fromarray(np.hstack([base, ov]).astype(np.uint8))


    tp = ROOT + "/testphotos"
    lines = ["", "damage % measured on your testphotos (green tint = leaf found, red = damage):"]
    if os.path.isdir(tp):
        for f in sorted(os.listdir(tp)):
            if f.lower().endswith((".jpg", ".jpeg", ".png")):
                try:
                    pct, frac, img = run_photo(os.path.join(tp, f)); img.save(OUT + "/check/" + os.path.splitext(f)[0] + ".jpg", quality=80)
                    lines.append("%-48s damage %5.1f %%   leaf area %4.0f %%" % (f[:48], pct, 100 * frac))
                except Exception as e:
                    lines.append("%-48s FAILED %s" % (f[:48], e))
    report += lines
    open(OUT + "/report.txt", "w").write("\n".join(report)); print("\n".join(report)); print("done. see", OUT)
