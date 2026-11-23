"""Builds data\\interim_v4 = interim_v3 + a new class  none__not_plant  (photos that are NOT a crop leaf/pest).
Why: the shipped model has to pick one of its crop classes for ANY photo, so a hand, wall, soil or a very blurry shot
gets a confident wrong answer. With this class the model can say "this is not a plant photo".

Run on the PC (needs internet, ~1.5 GB download, ~10 min):   python get_negatives.py
Then train:                                                  .\\run_v7.ps1
Negatives = COCO val2017 everyday scenes + DTD textures + degraded copies of real leaf photos (very blurry, dark, tiny/far away).
"""
import csv, hashlib, io, os, random, sys, tarfile, urllib.request, zipfile
from PIL import Image, ImageFilter, ImageEnhance

ROOT = r"D:\OCDD"
OUT = os.path.join(ROOT, "data", "negatives")
V3 = os.path.join(ROOT, "data", "interim_v3", "manifest.csv")
V4DIR = os.path.join(ROOT, "data", "interim_v4")
SRC = {
    "coco": ("http://images.cocodataset.org/zips/val2017.zip", "val2017.zip"),
    "dtd": ("https://www.robots.ox.ac.uk/~vgg/data/dtd/download/dtd-r1.0.1.tar.gz", "dtd.tar.gz"),
}
random.seed(7)


def fetch(url, dst):
    if os.path.exists(dst) and os.path.getsize(dst) > 1_000_000:
        return True
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    print("downloading", url, flush=True)
    try:
        def hook(b, bs, total):
            if total > 0 and b % 400 == 0:
                print("   %3d%%" % min(100, b * bs * 100 // total), end="\r", flush=True)
        urllib.request.urlretrieve(url, dst, hook)
        print()
        return True
    except Exception as e:
        print("  FAILED (%s) - skipping this source" % e)
        return False


def save_small(im, path, side=512):
    im = im.convert("RGB")
    w, h = im.size
    s = side / min(w, h)
    if s < 1:
        im = im.resize((round(w * s), round(h * s)), Image.BILINEAR)
    im.save(path, quality=90)


def main():
    dl = os.path.join(OUT, "_dl")
    scenes = os.path.join(OUT, "scenes")
    degraded = os.path.join(OUT, "degraded")
    os.makedirs(scenes, exist_ok=True); os.makedirs(degraded, exist_ok=True)
    made = []   # (path, tag)

    # 1. COCO scenes + DTD textures
    if fetch(SRC["coco"][0], os.path.join(dl, SRC["coco"][1])):
        with zipfile.ZipFile(os.path.join(dl, SRC["coco"][1])) as z:
            names = [n for n in z.namelist() if n.lower().endswith(".jpg")]
            for n in names:
                p = os.path.join(scenes, "coco_" + os.path.basename(n))
                if not os.path.exists(p):
                    try: save_small(Image.open(io.BytesIO(z.read(n))), p)
                    except Exception: continue
                made.append((p, "scene"))
    if fetch(SRC["dtd"][0], os.path.join(dl, SRC["dtd"][1])):
        with tarfile.open(os.path.join(dl, SRC["dtd"][1])) as t:
            for m in t.getmembers():
                if m.isfile() and m.name.lower().endswith(".jpg") and "/images/" in m.name:
                    p = os.path.join(scenes, "dtd_" + os.path.basename(m.name))
                    if not os.path.exists(p):
                        try: save_small(Image.open(io.BytesIO(t.extractfile(m).read())), p)
                        except Exception: continue
                    made.append((p, "texture"))

    # 2. degraded copies of real field photos from the manifest: unusable photos must be "not a plant photo" too
    rows = list(csv.DictReader(open(V3, newline="", encoding="utf-8")))
    field = [r for r in rows if r["domain"] == "field" and r["split"] == "train" and r["crop"] != "pest"]
    random.shuffle(field)
    n_deg = 2500
    for i, r in enumerate(field[:n_deg]):
        p = os.path.join(degraded, "deg_%05d.jpg" % i)
        if not os.path.exists(p):
            try:
                im = Image.open(r["path"]).convert("RGB")
                im.thumbnail((640, 640))
                kind = i % 4
                if kind == 0:   im = im.filter(ImageFilter.GaussianBlur(random.uniform(9, 20)))             # very blurry
                elif kind == 1: im = ImageEnhance.Brightness(im).enhance(random.uniform(0.04, 0.15))        # far too dark
                elif kind == 2:                                                                             # leaf is a tiny part of a big scene
                    w, h = im.size; s = random.uniform(0.07, 0.14)
                    sm = im.resize((max(8, int(w * s)), max(8, int(h * s))), Image.BILINEAR)
                    bg = Image.open(random.choice(made)[0]).convert("RGB").resize((w, h)) if made else Image.new("RGB", (w, h), (120, 110, 100))
                    bg.paste(sm, (random.randint(0, w - sm.width), random.randint(0, h - sm.height))); im = bg
                else:           im = ImageEnhance.Brightness(im).enhance(random.uniform(2.6, 4.0)).filter(ImageFilter.GaussianBlur(4))  # blown out
                im.save(p, quality=88)
            except Exception:
                continue
        made.append((p, "degraded"))

    print("negatives:", len(made), {t: sum(1 for _, x in made if x == t) for t in ("scene", "texture", "degraded")})
    if len(made) < 500:
        sys.exit("too few negatives - check the internet connection and run again")

    # 3. manifest v4 = v3 + negatives (split by a hash of the file name: 80/10/10)
    os.makedirs(V4DIR, exist_ok=True)
    out = os.path.join(V4DIR, "manifest.csv")
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        for r in rows: w.writerow(r)
        for p, tag in made:
            h = int(hashlib.md5(os.path.basename(p).encode()).hexdigest(), 16) % 10
            w.writerow({"path": p, "crop": "none", "label": "not_plant", "source": "neg_" + tag, "domain": "field",
                        "width": 0, "height": 0, "group": "neg_" + os.path.basename(p),
                        "split": "train" if h < 8 else ("val" if h == 8 else "test")})
    print("wrote", out)


if __name__ == "__main__":
    main()
