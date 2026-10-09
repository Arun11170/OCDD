"""Adds PlantSeg field photos (CC0 / CC-BY) to the training list for the classes the app already has.
  python add_plantseg.py            -> writes data\\interim_v3\\manifest.csv (interim_v2 + PlantSeg), prints a report
PlantSeg's own Training/Validation/Test split is kept, so its test photos give a much bigger honest FIELD test.
Nothing is deleted or changed in interim_v2."""
import csv, os, sys, collections
from PIL import Image
ROOT = os.environ.get("OCDD_ROOT") or ("D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD")
WIN = r"D:\OCDD"
PS = ROOT + "/data/raw/plantseg/data/Plantseg"
# PlantSeg disease -> (crop, label) in the app. Only matches I am confident of; anything else is left out.
MAP = {
    "apple scab": ("apple", "apple_scab"), "apple black rot": ("apple", "black_rot"), "apple rust": ("apple", "cedar_apple_rust"),
    "banana black leaf streak": ("banana", "black_sigatoka_disease"), "banana panama disease": ("banana", "panama_disease"),
    "bell pepper bacterial spot": ("capsicum", "bacterial_spot"),
    "grape black rot": ("grape", "black_rot"),
    "corn rust": ("maize", "common_rust"), "corn northern leaf blight": ("maize", "blight"), "corn gray leaf spot": ("maize", "gray_leaf_spot"),
    "potato early blight": ("potato", "early_blight"), "potato late blight": ("potato", "late_blight"),
    "rice blast": ("rice", "rice_blast"),
    "tomato early blight": ("tomato", "plantseg_early_blight"), "tomato late blight": ("tomato", "plantseg_late_blight"),
    "tomato leaf mold": ("tomato", "plantseg_leaf_mold"), "tomato septoria leaf spot": ("tomato", "plantseg_septoria_leaf_spot"),
    "tomato bacterial leaf spot": ("tomato", "plantseg_bacterial_spot"), "tomato yellow leaf curl virus": ("tomato", "plantseg_yellow_leaf_curl_virus"),
    "tomato mosaic virus": ("tomato", "plantseg_mosaic_virus"),
    "wheat stripe rust": ("wheat", "stripe_rust"), "wheat septoria blotch": ("wheat", "septoria"),
}
SPLIT = {"Training": "train", "Validation": "val", "Test": "test"}
meta = list(csv.DictReader(open(PS + "/Metadata.csv", encoding="utf-8", errors="replace")))
folder = {"train": "train", "val": "val", "test": "test"}
base = list(csv.DictReader(open(ROOT + "/data/interim_v2/manifest.csv", encoding="utf-8")))
out, cnt, lic, skipped = [], collections.Counter(), collections.Counter(), 0
for r in meta:
    m = MAP.get(r["Disease"].strip().lower())
    if not m: continue
    sp = SPLIT.get(r["Split"].strip())
    f = "%s/images/%s/%s" % (PS, folder[sp], r["Name"])
    if not os.path.isfile(f): skipped += 1; continue
    try:
        w, h = Image.open(f).size
    except Exception:
        skipped += 1; continue
    p = WIN + "\\data\\raw\\plantseg\\data\\Plantseg\\images\\%s\\%s" % (folder[sp], r["Name"])
    out.append({"path": p, "crop": m[0], "label": m[1], "source": "plantseg", "domain": "field", "width": w, "height": h,
                "group": "ps_" + r["Name"], "split": sp})
    cnt[(m[0] + "__" + m[1].replace("plantseg_", ""), sp)] += 1; lic[r["License"]] += 1
os.makedirs(ROOT + "/data/interim_v3", exist_ok=True)
with open(ROOT + "/data/interim_v3/manifest.csv", "w", newline="", encoding="utf-8") as f:
    wr = csv.DictWriter(f, fieldnames=list(base[0].keys())); wr.writeheader()
    for r in base + out: wr.writerow({k: r.get(k, "") for k in base[0].keys()})
print("added %d PlantSeg photos (skipped %d missing/unreadable); licences %s" % (len(out), skipped, dict(lic)))
print("%-42s train   val  test" % "class")
for c in sorted({k[0] for k in cnt}):
    print("%-42s %5d %5d %5d" % (c, cnt[(c, "train")], cnt[(c, "val")], cnt[(c, "test")]))
open(ROOT + "/data/interim_v3/ATTRIBUTION.txt", "w").write("PlantSeg (Wei et al.), photos licensed CC0 or CC-BY; the CC-BY ones need credit to the PlantSeg authors and original photographers (URLs in Metadata.csv).\n")
