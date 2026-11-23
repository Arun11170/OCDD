"""Writes the app's gates.json from accuracy_lab_gates.json (per-crop confidence gates measured on the held-out test split).
Only gates ABOVE the base 0.80 are written (the app never goes below 0.80); a crop whose target is "not reachable" (null) gets 0.95,
which makes the app say "not sure" for almost everything of that crop instead of guessing. Capped at 0.95.
usage: python tools\\make_gates.py <accuracy_lab_gates.json> <assets\\gates.json>"""
import json, sys
src, dst = sys.argv[1], sys.argv[2]
g = json.load(open(src, encoding="utf-8"))
out = {}
for crop, v in g.items():
    if crop == "none": continue
    gate = 0.95 if v is None else float(v)
    if gate > 0.80: out[crop] = round(min(0.95, gate), 2)
json.dump(out, open(dst, "w", encoding="utf-8"))
print("gates written:", out)
