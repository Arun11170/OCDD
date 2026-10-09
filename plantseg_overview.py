"""Prints what is inside PlantSeg (so crops/diseases can be matched to the app's classes). Run after get_data.py plantseg:
    python plantseg_overview.py > plantseg_overview.txt
"""
import csv, glob, os, collections
ROOT = os.environ.get("OCDD_ROOT") or ("D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD")
D = ROOT + "/data/raw/plantseg/data"
print("folders:")
for p in sorted(glob.glob(D + "/*")) + sorted(glob.glob(D + "/*/*")):
    if os.path.isdir(p): print("  ", p.replace(D, ""), len(os.listdir(p)))
metas = glob.glob(D + "/**/*Meta*.csv", recursive=True)
print("meta files:", metas)
for m in metas[:1]:
    rows = list(csv.DictReader(open(m, encoding="utf-8", errors="replace")))
    print("columns:", list(rows[0].keys()), " rows:", len(rows))
    print("first rows:"); [print("  ", r) for r in rows[:3]]
    for key in rows[0].keys():
        vals = collections.Counter(r[key] for r in rows)
        if 3 <= len(vals) <= 200 and key.lower() not in ("name", "filename", "image", "path"):
            print("\n== %s (%d values)" % (key, len(vals)))
            for v, c in vals.most_common(): print("   %5d  %s" % (c, v))
