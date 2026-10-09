import json,glob
F=json.load(open("check_features.json",encoding="utf-8")); H=json.load(open("check_features_hi.json",encoding="utf-8"))
kb={e["class_id"]:e for e in json.load(open("kb.json",encoding="utf-8"))["entries"]}
tables={};family={};why={}
for f in sorted(glob.glob("checks_g*.json")):
    j=json.load(open(f,encoding="utf-8")); tables.update(j["tables"]); family.update(j.get("family",{})); why.update(j.get("why",{}))
ids={x["id"] for x in F}
bad=[(c,k) for c,t in tables.items() for k in t if k not in ids]
assert not bad,bad
missing=[c for c in kb if c not in tables]
print("classes with tables:",len(tables),"kb entries:",len(kb),"missing:",missing)
out={"features":[{"id":x["id"],"kind":x["kind"],"en":x["en"],"hi":H[x["id"]]} for x in F],"tables":tables,"family":family}
json.dump(out,open("checks.json","w",encoding="utf-8"),ensure_ascii=False,separators=(",",":"))
json.dump(why,open("checks_why.json","w",encoding="utf-8"),ensure_ascii=False,indent=1)
import os; print(os.path.getsize("checks.json"),"bytes")
