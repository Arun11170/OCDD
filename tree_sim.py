"""Python copy of the app's symptom-tree engine (Checks.kt) for testing it without a phone.
   python tree_sim.py            -> simulated rescue rates (a farmer who answers exactly as the tables say = best case)"""
import json, math, os, random
ROOT = os.path.dirname(os.path.abspath(__file__))
T = json.load(open(os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "assets", "symptom_tree.json"), encoding="utf-8"))
NOISE, SURE, MINQ, MIN_GAIN, MAXQ, FLOOR, CROP_MIN = 0.04, 0.95, 3, 0.03, 14, 0.02, 0.12
LEAD, TYPICAL, MIN_TRACE = 0.6, 0.7, 2
feat, tab, cls_of = {}, {}, {}
for crop, qs in T.items():
    for q in qs:
        feat[q["id"]] = crop
        for c, p in q["a"].items():
            tab.setdefault(c, {})[q["id"]] = p
            if c not in cls_of.setdefault(crop, []): cls_of[crop].append(c)

def lik(c, f, yes):
    p = tab.get(c, {}).get(f)
    if p is None: return 0.5
    q = NOISE + (1 - 2 * NOISE) * p
    return q if yes else 1 - q

def H(d): return -sum(v * math.log2(v) for v in d.values() if v > 0)

def prior_from(ranked):            # ranked: {class: prob}
    by = {}
    for c, p in ranked.items(): by[c.split("__")[0]] = by.get(c.split("__")[0], 0) + p
    s = sorted(by.items(), key=lambda kv: -kv[1]); crops = []
    for i, (c, v) in enumerate(s):
        if len(crops) >= 2: break
        if c not in cls_of: continue
        if i == 0 or v >= CROP_MIN: crops.append(c)
    m = {}
    for c in crops:
        for cl in cls_of[c]: m[cl] = max(ranked.get(cl, 0.0), FLOOR)
    z = sum(m.values()); return {k: v / z for k, v in m.items()}, crops

def run(ranked, oracle):
    prior, crops = prior_from(ranked)
    if not prior: return None, 0
    qs = [f for f, c in feat.items() if c in crops]
    ans, asked = [], set()
    def post():
        w = dict(prior)
        for f, y in ans:
            for c in w: w[c] *= lik(c, f, y)
        s = sum(w.values()); return {c: v / s for c, v in w.items()}
    while True:
        cur = post(); lead = max(cur, key=cur.get); top = cur[lead]
        if len(asked) >= MAXQ: break
        if top >= LEAD and len(asked) >= MIN_TRACE:
            rest = [f for f in qs if f not in asked and tab.get(lead, {}).get(f, 0) >= TYPICAL]
            if rest:
                def g(f):
                    py = sum(v * lik(c, f, True) for c, v in cur.items())
                    if py <= 1e-9 or py >= 1 - 1e-9: return 0
                    return H(cur) - sum(pr * H({c: v * lik(c, f, y) / pr for c, v in cur.items()}) for y, pr in ((True, py), (False, 1 - py)))
                f = max(rest, key=lambda f: (tab[lead][f], g(f)))
                asked.add(f); ans.append((f, oracle(f))); continue
        if top >= SURE and len(asked) >= MINQ: break
        best, bg = None, 0
        for f in qs:
            if f in asked: continue
            py = sum(v * lik(c, f, True) for c, v in cur.items())
            if py <= 1e-9 or py >= 1 - 1e-9: continue
            h = sum(pr * H({c: v * lik(c, f, y) / pr for c, v in cur.items()}) for y, pr in ((True, py), (False, 1 - py)))
            g_ = H(cur) - h
            if g_ > bg: bg, best = g_, f
        if not best or bg < (0.005 if top >= SURE else MIN_GAIN): break
        asked.add(best); ans.append((best, oracle(best)))
    cur = post(); return max(cur, key=cur.get), len(ans)

def oracle_for(true, rng):
    def o(f):
        p = tab.get(true, {}).get(f, 0.1)
        return p >= 0.5 or (0.3 < p < 0.5 and rng.random() < p)
    return o

if __name__ == "__main__":
    for label, flip in (("truthful farmer", 0.0), ("farmer wrong on 8% of answers", 0.08)):
        rng = random.Random(1); ok = tot = 0; nq = 0; okc = totc = 0
        for crop, cl in cls_of.items():
            for true in cl:
                base = oracle_for(true, rng)
                o = (lambda f, base=base: (not base(f)) if rng.random() < flip else base(f))
                for photo in cl:
                    if photo == true: continue
                    r = {c: (0.7 if c == photo else 0.02) for c in cl}
                    res, n = run(r, o); tot += 1; ok += res == true; nq += n
                # photo already right (typical case)
                r = {c: (0.8 if c == true else 0.02) for c in cl}
                res, n = run(r, o); totc += 1; okc += res == true
        print("%-32s photo wrong: tree recovers the true problem %.1f%% of %d | photo right: stays right %.1f%% | avg questions %.1f"
              % (label, 100 * ok / tot, tot, 100 * okc / totc, nq / tot))
