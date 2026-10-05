"""OCDD data prep: audit every image, remove near-duplicates leakage, build manifest with split.

Run (PowerShell):  python D:\\OCDD\\prepare_data.py            (add --rice for the rice stretch set)
Output: D:\\OCDD\\data\\interim\\manifest.csv, audit_report.txt, tomato_pv\\ (images pulled out of parquet)

What it does
 1. finds every image of every dataset (original photos only, never the pre-augmented folders)
 2. opens each one (corrupt files are reported and skipped) and computes a perceptual hash
 3. groups near-identical photos (same leaf, same shot) into one group_id
 4. splits 70/15/15 by GROUP, per class, so no near-duplicate crosses train/val/test
 5. Maharashtra tomato (different classes, real field photos) is kept aside as split=field_test
"""
import argparse, csv, io, os, random, re, sys, subprocess
from collections import defaultdict, Counter
from concurrent.futures import ProcessPoolExecutor

try:
    from PIL import Image
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", "pillow"])
    from PIL import Image

EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def clean_class(name):
    n = re.sub(r"^class\s*\d+\s*-\s*", "", name.strip(), flags=re.I)
    return re.sub(r"\s+", "_", n.strip().lower())


def walk_images(folder):
    out = []
    for dp, _, fs in os.walk(folder):
        for f in fs:
            if os.path.splitext(f)[1].lower() in EXT:
                out.append(os.path.join(dp, f))
    return out


def dhash(img, size=16):
    g = img.convert("L").resize((size + 1, size), Image.BILINEAR)
    px = list(g.get_flattened_data()) if hasattr(g, "get_flattened_data") else list(g.getdata())
    bits = 0
    for r in range(size):
        for c in range(size):
            bits = (bits << 1) | (1 if px[r * (size + 1) + c] > px[r * (size + 1) + c + 1] else 0)
    return bits


def check(path):
    """returns (path, ok, w, h, hash or error)"""
    try:
        with Image.open(path) as im:
            im.draft("RGB", (256, 256))      # fast JPEG decode at low size
            w, h = im.size
            im.load()
            return (path, True, w, h, dhash(im))
    except Exception as e:                    # noqa
        return (path, False, 0, 0, str(e)[:80])


def collect(root, include_rice):
    """list of (path, crop, label, source, domain)"""
    raw = os.path.join(root, "data", "raw")
    items = []

    def add_dir(base, crop, source, domain, label_from="parent"):
        for p in walk_images(base):
            items.append((p, crop, clean_class(os.path.basename(os.path.dirname(p))), source, domain))

    # groundnut
    for dp, dn, _ in os.walk(os.path.join(raw, "groundnut")):
        if os.path.basename(dp) == "Raw_Data":
            add_dir(dp, "groundnut", "groundnut", "field")
    # chilli: only the resized raw images (the augment/cropped folders would leak)
    for dp, dn, _ in os.walk(os.path.join(raw, "chilli")):
        if os.path.basename(dp) == "resized_raw images" and "resized_raw images" in os.path.basename(os.path.dirname(dp)) + "resized_raw images":
            if any(os.path.isdir(os.path.join(dp, d)) for d in os.listdir(dp)):
                add_dir(dp, "chilli", "chilli", "field")
    # okra: pool the 3 provided splits, we split again ourselves
    for dp, dn, _ in os.walk(os.path.join(raw, "okra")):
        if os.path.basename(dp) in ("Training", "Validation", "Testing"):
            add_dir(dp, "okra", "okra", "field")
    # PlantDoc tomato only
    for p in walk_images(os.path.join(raw, "PlantDoc")):
        lab = os.path.basename(os.path.dirname(p))
        if "tomato" in lab.lower():
            items.append((p, "tomato", "plantdoc_" + clean_class(lab), "plantdoc", "field"))
    # Maharashtra real field tomato photos: kept as an extra field test set
    for p in walk_images(os.path.join(raw, "tomato_maharashtra")):
        parts = os.path.normpath(p).split(os.sep)
        lab = parts[parts.index("Real world data") + 1] if "Real world data" in parts else os.path.basename(os.path.dirname(p))
        items.append((p, "tomato", "mh_" + clean_class(lab), "maharashtra", "field_extra"))
    # rice (stretch): Original Dataset only
    if include_rice:
        for dp, dn, _ in os.walk(os.path.join(raw, "rice")):
            if os.path.basename(dp) == "Original Dataset":
                add_dir(dp, "rice", "rice", "field")
    return items


def extract_plantvillage(root):
    """pull the 10 tomato classes out of the HF parquet files into interim\\tomato_pv\\<class>\\n.jpg"""
    out_root = os.path.join(root, "data", "interim", "tomato_pv")
    done_flag = os.path.join(out_root, "_done.txt")
    if os.path.exists(done_flag):
        return walk_images(out_root)
    try:
        import pyarrow.parquet as pq
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", "pyarrow"])
        import pyarrow.parquet as pq
    data = os.path.join(root, "data", "raw", "tomato_plantvillage", "data")
    names = None
    n_written = 0
    for fn in sorted(os.listdir(data)):
        if not fn.endswith(".parquet"):
            continue
        pf = pq.ParquetFile(os.path.join(data, fn))
        if names is None:
            import json
            meta = json.loads(pf.schema_arrow.metadata[b"huggingface"])
            names = meta["info"]["features"]["label"]["names"]
        for batch in pf.iter_batches(batch_size=256):
            d = batch.to_pydict()
            for img, lab in zip(d["image"], d["label"]):
                cname = names[lab]
                if not cname.startswith("Tomato___"):
                    continue
                cdir = os.path.join(out_root, clean_class(cname.split("___", 1)[1]))
                os.makedirs(cdir, exist_ok=True)
                n_written += 1
                with open(os.path.join(cdir, "%s_%06d.jpg" % (fn[:5], n_written)), "wb") as f:
                    f.write(img["bytes"])
    open(done_flag, "w").write("ok")
    return walk_images(out_root)


class UF:
    def __init__(self, n): self.p = list(range(n))
    def f(self, x):
        while self.p[x] != x:
            self.p[x] = self.p[self.p[x]]; x = self.p[x]
        return x
    def u(self, a, b): self.p[self.f(a)] = self.f(b)


BANDS = 11   # 256-bit hash, <=10 differing bits => at least one of 11 bands is identical


def group_near_duplicates(rows, max_ham=10):
    """rows: list of dict with 'hash'. Same-class only. Pigeonhole banding keeps this fast."""
    uf = UF(len(rows))
    buckets = defaultdict(list)
    for i, r in enumerate(rows):
        key_cls = (r["crop"], r["label"])
        for b in range(BANDS):
            lo = (256 * b) // BANDS; hi = (256 * (b + 1)) // BANDS
            buckets[(key_cls, b, (r["hash"] >> lo) & ((1 << (hi - lo)) - 1))].append(i)
    for idxs in buckets.values():
        if len(idxs) < 2 or len(idxs) > 400:      # skip huge degenerate buckets (blank images)
            continue
        for a in range(len(idxs)):
            for b in range(a + 1, len(idxs)):
                i, j = idxs[a], idxs[b]
                if uf.f(i) != uf.f(j) and bin(rows[i]["hash"] ^ rows[j]["hash"]).count("1") <= max_ham:
                    uf.u(i, j)
    return [uf.f(i) for i in range(len(rows))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=r"D:\OCDD")
    ap.add_argument("--rice", action="store_true")
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()
    root = a.root
    interim = os.path.join(root, "data", "interim")
    os.makedirs(interim, exist_ok=True)
    report = []
    def say(s=""):
        print(s); report.append(s)

    say("1/4 finding images ...")
    items = collect(root, a.rice)
    pv_dir = os.path.join(root, "data", "raw", "tomato_plantvillage", "data")
    if os.path.isdir(pv_dir):
        say("    extracting tomato classes from PlantVillage parquet (once, a few minutes) ...")
        for p in extract_plantvillage(root):
            items.append((p, "tomato", "pv_" + clean_class(os.path.basename(os.path.dirname(p))), "plantvillage", "lab"))
    say("    found %d image files" % len(items))

    say("2/4 opening every image + hashing (multi-core) ...")
    paths = [it[0] for it in items]
    rows, bad = [], []
    with ProcessPoolExecutor() as ex:
        for k, (it, res) in enumerate(zip(items, ex.map(check, paths, chunksize=64))):
            path, ok, w, h, val = res
            if not ok:
                bad.append((path, val)); continue
            rows.append(dict(path=path, crop=it[1], label=it[2], source=it[3], domain=it[4], w=w, h=h, hash=val))
            if k % 5000 == 0 and k:
                print("    %d / %d" % (k, len(items)))
    say("    readable: %d   corrupt: %d" % (len(rows), len(bad)))

    say("3/4 grouping near-duplicates ...")
    gid = group_near_duplicates(rows)
    for r, g in zip(rows, gid):
        r["group"] = "g%d" % g
    n_groups = len(set(gid))
    say("    %d images -> %d groups (%d images share a group with another photo)" % (
        len(rows), n_groups, len(rows) - n_groups))

    say("4/4 splitting by group, per class (70/15/15) ...")
    rng = random.Random(a.seed)
    by_cls = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if r["domain"] == "field_extra":
            r["split"] = "field_test"; continue
        by_cls[(r["crop"], r["label"])][r["group"]].append(r)
    for cls, groups in by_cls.items():
        gl = list(groups.items()); rng.shuffle(gl)
        total = sum(len(v) for _, v in gl); seen = 0
        for _, imgs in gl:
            frac = seen / total
            sp = "train" if frac < 0.70 else ("val" if frac < 0.85 else "test")
            for r in imgs: r["split"] = sp
            seen += len(imgs)

    man = os.path.join(interim, "manifest.csv")
    with open(man, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["path", "crop", "label", "source", "domain", "width", "height", "group", "split"])
        for r in rows:
            w.writerow([r["path"], r["crop"], r["label"], r["source"], r["domain"], r["w"], r["h"], r["group"], r["split"]])

    # leakage self-check: no group in more than one split
    gs = defaultdict(set)
    for r in rows: gs[r["group"]].add(r["split"])
    leaks = sum(1 for s in gs.values() if len(s) > 1)
    say("    leakage check: %d groups appear in more than one split (must be 0)" % leaks)

    say(""); say("=== images per class and split ===")
    cnt = defaultdict(Counter)
    for r in rows: cnt[(r["crop"], r["label"])][r["split"]] += 1
    say("%-10s %-40s %6s %6s %6s %6s" % ("crop", "class", "train", "val", "test", "field"))
    for (c, l), s in sorted(cnt.items()):
        say("%-10s %-40s %6d %6d %6d %6d" % (c, l[:40], s["train"], s["val"], s["test"], s["field_test"]))
    small = [(c, l, sum(s.values())) for (c, l), s in cnt.items() if sum(s.values()) < 60]
    if small:
        say(""); say("classes with fewer than 60 images (weak): " + ", ".join("%s/%s=%d" % x for x in small))
    sizes = Counter(("<224" if min(r["w"], r["h"]) < 224 else "ok") for r in rows)
    say("images with a side under 224 px: %d" % sizes["<224"])
    if bad:
        say(""); say("corrupt files:")
        for p, e in bad[:50]: say("   %s  (%s)" % (p, e))
    open(os.path.join(interim, "audit_report.txt"), "w", encoding="utf-8").write("\n".join(report))
    say(""); say("wrote %s" % man)
    say("Done. Paste the table above to Claude.")


if __name__ == "__main__":
    main()
