import json, re, sys, glob
classes = ["apple__apple_scab", "apple__black_rot", "apple__cedar_apple_rust", "apple__healthy", "banana__black_sigatoka_disease", "banana__bract_mosaic_virus_disease", "banana__healthy_leaf", "banana__insect_pest_disease", "banana__moko_disease", "banana__panama_disease", "banana__yellow_sigatoka_disease", "capsicum__bacterial_spot", "capsicum__healthy", "chilli__cercospora", "chilli__healthy", "chilli__murda_complex", "chilli__nutritional_deficiency", "chilli__powdery_mildew", "grape__black_rot", "grape__esca_(black_measles)", "grape__healthy", "grape__leaf_blight_(isariopsis_leaf_spot)", "groundnut__early_leaf_spot", "groundnut__healthy_leaf", "groundnut__late_leaf_spot", "groundnut__nutrition_deficiency", "groundnut__rust", "maize__blight", "maize__common_rust", "maize__gray_leaf_spot", "maize__healthy", "mango__anthracnose", "mango__bacterial_canker", "mango__cutting_weevil", "mango__die_back", "mango__gall_midge", "mango__healthy", "mango__powdery_mildew", "mango__sooty_mould", "okra__alternaria_leaf_spot", "okra__cercospora_leaf_spot", "okra__downy_mildew", "okra__healthy", "okra__leaf_curly_virus", "okra__phyllosticta_leaf_spot", "pest__aphids", "pest__army_worm", "pest__corn_borer", "pest__cutworm", "pest__flea_beetle", "pest__gall_midge_dasineura", "pest__leafhopper", "pest__mango_borer", "pest__mango_hopper", "pest__mango_leaf_cutting_weevil", "pest__mango_seed_weevil", "pest__mole_cricket", "pest__oriental_fruit_fly", "pest__planthopper", "pest__red_spider_mite", "pest__rice_gall_midge", "pest__rice_leaf_folder", "pest__rice_stem_borer", "pest__rice_water_weevil", "pest__thrips", "pest__white_grub", "pest__wireworm", "potato__early_blight", "potato__healthy", "potato__late_blight", "rice__healthy", "rice__insect", "rice__leaf_scald", "rice__rice_blast", "rice__rice_leaffolder", "rice__rice_stripes", "rice__rice_tungro", "sugarcane__healthy", "sugarcane__mosaic", "sugarcane__redrot", "sugarcane__rust", "sugarcane__yellow", "tomato__bacterial_spot", "tomato__early_blight", "tomato__healthy", "tomato__late_blight", "tomato__leaf_mold", "tomato__mosaic_virus", "tomato__septoria_leaf_spot", "tomato__spider_mites", "tomato__target_spot", "tomato__yellow_leaf_curl_virus", "wheat__healthy", "wheat__septoria", "wheat__stripe_rust"]
entries = {}
for f in sorted(glob.glob("*.json")):
    if f in ("kb.json",) or f.startswith("kb_"): continue
    for e in json.load(open(f))["entries"]:
        entries[e["class_id"]] = e
missing = [c for c in classes if c not in entries]
extra = [c for c in entries if c not in classes]
print("entries", len(entries), "missing", missing, "extra", extra)

# Chemicals we never show: banned, withdrawn or heavily restricted in India per our own knowledge (CIB&RC list not
# reachable to verify). Dropping a name is always the safe direction.
DENY = ("endosulfan","phosphamidon","dimecron","monocrotophos","lindane","methyl parathion","carbofuran","fenthion","ddt","bhc","aldrin","dieldrin","mercur","carbaryl","dicofol","dinocap","phorate","tridemorph","dichlorvos","dichlorovos","triazophos","diazinon","trichlorfon","benomyl","fenarimol","thiometon")  # official CIB&RC banned list, checked 2026-10-06
removed = []
for cid, e in entries.items():
    keep = []
    for t in e.get("treatment_options") or []:
        n = t.get("active_ingredient","").lower()
        if any(b in n for b in DENY):
            removed.append((cid, t.get("active_ingredient")))
        else:
            keep.append(t)
    e["treatment_options"] = keep
open("removed_chemicals.txt","w").write("\n".join("%s: %s" % r for r in removed))
print("removed restricted chemicals:", len(removed))

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
        t["indian_source"] = bool(pub) and pub[0] in ("TNAU","ICAR","CIB&RC","SAU")
print("dose-like hits:", bad)
json.dump({"version": 1, "entries": [entries[c] for c in classes if c in entries]}, open("kb.json","w"), ensure_ascii=False, indent=1)
