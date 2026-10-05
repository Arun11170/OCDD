import json, re, sys, glob
classes = ["chilli__cercospora","chilli__healthy","chilli__murda_complex","chilli__nutritional_deficiency","chilli__powdery_mildew",
"groundnut__early_leaf_spot","groundnut__healthy_leaf","groundnut__late_leaf_spot","groundnut__nutrition_deficiency","groundnut__rust",
"okra__alternaria_leaf_spot","okra__cercospora_leaf_spot","okra__downy_mildew","okra__healthy","okra__leaf_curly_virus","okra__phyllosticta_leaf_spot",
"tomato__bacterial_spot","tomato__early_blight","tomato__healthy","tomato__late_blight","tomato__leaf_mold","tomato__mosaic_virus",
"tomato__septoria_leaf_spot","tomato__spider_mites","tomato__target_spot","tomato__yellow_leaf_curl_virus"]
entries = {}
for f in sorted(glob.glob("*.json")):
    if f in ("kb.json",): continue
    for e in json.load(open(f))["entries"]:
        entries[e["class_id"]] = e
missing = [c for c in classes if c not in entries]
extra = [c for c in entries if c not in classes]
print("entries", len(entries), "missing", missing, "extra", extra)
DOSE = re.compile(r"\d+(\.\d+)?\s*(ml|g|kg|l|litre|liter|ha|acre|ppm|%|per cent|percent|gm|mg)\b|\bper (ha|acre|litre|liter)\b", re.I)
bad = 0
for cid, e in entries.items():
    blob = json.dumps({k: v for k, v in e.items() if k not in ("sources",)}, ensure_ascii=False)
    for m in DOSE.finditer(blob):
        print("DOSE-LIKE", cid, blob[max(0,m.start()-40):m.end()+20]); bad += 1
    for k in ("symptoms","cultural_actions"):
        for line in e.get(k) or []:
            if len(line.split()) > 30: print("LONG", cid, k, len(line.split()))
    if not e.get("sources"): print("NO SOURCES", cid)
    for t in e.get("treatment_options") or []:
        pub = [s.get("publisher") for s in e["sources"] if s.get("url") == t.get("source_url")]
        t["indian_source"] = bool(pub) and pub[0] in ("TNAU","ICAR","CIB&RC")
print("dose-like hits:", bad)
json.dump({"version": 1, "entries": [entries[c] for c in classes if c in entries]}, open("kb.json","w"), ensure_ascii=False, indent=1)
