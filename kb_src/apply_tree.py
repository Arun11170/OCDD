"""Merge the new questions (tree_add_crops.py, tree_add_pests.py) into assets/symptom_tree.json.  Safe to re-run (skips ids already there)."""
import json, os, sys, shutil, importlib
sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE); sys.path.insert(0, HERE)
F = os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "assets", "symptom_tree.json")
os.makedirs(os.path.join(ROOT, "_backup_tree"), exist_ok=True)
bk = os.path.join(ROOT, "_backup_tree", "symptom_tree.original.json")
if not os.path.exists(bk): shutil.copy(F, bk)
T = json.load(open(F, encoding="utf-8"))
mods = [m for m in ("tree_add_crops", "tree_add_pests") if os.path.exists(os.path.join(HERE, m + ".py"))]
added = 0
for m in mods:
    ADD = importlib.import_module(m).ADD
    for crop, items in ADD.items():
        qs = T[crop]; have = {q["id"] for q in qs}
        cls = sorted({c for q in qs for c in q["a"]})
        n = len(qs)
        for slug, en, hi, hot, dflt in items:
            qid = "%s_x_%s" % (crop, slug)
            if qid in have: continue
            a = {c: dflt for c in cls}
            for key, p in hot.items():
                hit = [c for c in cls if c == crop + "__" + key]
                if len(hit) != 1: raise SystemExit("cannot resolve %s in %s (%s)" % (key, crop, hit))
                a[hit[0]] = p
            qs.append({"id": qid, "en": en, "hi": hi, "a": a}); added += 1
json.dump(T, open(F, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("added", added, "questions;", sum(len(v) for v in T.values()), "in total")
