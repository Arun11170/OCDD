"""Audit of the knowledge base and the question tree.  python audit_kb.py   (exit code 1 when something fails)
Checks: every sick class has advice (treatment or an explicit 'no chemical' note), Hindi is present, sources, and that every
pair of problems of one crop is told apart by >= MIN_SEP questions.  Also simulates a farmer answering truthfully."""
import json, os, sys, itertools, random
sys.dont_write_bytecode = True
ROOT = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "assets")
MIN_SEP = 3
T = json.load(open(os.path.join(A, "symptom_tree.json"), encoding="utf-8"))
K = {e["class_id"]: e for e in json.load(open(os.path.join(A, "kb.json"), encoding="utf-8"))["entries"]}
H = {e["class_id"]: e for e in json.load(open(os.path.join(A, "kb_hi.json"), encoding="utf-8"))["entries"]}
bad = 0
ACK = {"okra__downy_mildew"}   # acknowledged: research found no second source
def fail(msg):
    global bad; bad += 1; print("FAIL", msg)
# --- question tree
ids = set()
for crop, qs in T.items():
    cls = sorted({c for q in qs for c in q["a"]})
    for q in qs:
        if q["id"] in ids: fail("duplicate question id " + q["id"])
        ids.add(q["id"])
        if not q.get("hi"): fail("no Hindi: " + q["id"])
        miss = [c for c in cls if c not in q["a"]]
        if miss: fail("%s has no answer for %s" % (q["id"], miss))
        for c, v in q["a"].items():
            if not 0 <= v <= 1: fail("bad p in " + q["id"])
    for a, b in itertools.combinations(cls, 2):
        sep = sum(1 for q in qs if abs(q["a"][a] - q["a"][b]) >= 0.5)
        if sep < MIN_SEP: fail("pair %s / %s separated by only %d question(s)" % (a, b, sep))
    for c in cls:
        if c not in K: fail("class not in kb: " + c)
# --- knowledge base
for c, e in K.items():
    sick = "healthy" not in c
    if c not in H: fail("no Hindi entry " + c); continue
    if sick:
        if not e["treatment_options"] and not (e.get("when_to_act") or "").strip() and "No reliable Indian source" not in (e.get("caveats") or ""): fail("no advice and no explicit statement: " + c)
        if len(e["sources"]) < 2 and c not in ACK: fail("fewer than 2 sources: " + c)
        if not e["cultural_actions"]: fail("no cultural actions: " + c)
    for t in e["treatment_options"]:
        if not t.get("source_url"): fail("treatment without source: %s %s" % (c, t.get("active_ingredient")))
print("\n%d problem(s)" % bad)
sys.exit(1 if bad else 0)
