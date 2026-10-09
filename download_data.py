r"""Download extra field-photo datasets from Mendeley Data into data\incoming\<name>.
Safe to re-run: finished files are skipped. Writes SOURCE.txt (link + licence) per set.
Usage:  python download_data.py            (all)
        python download_data.py okra      (one)
"""
import json, os, sys, time, zipfile, urllib.request

ROOT = os.path.join(os.environ.get("OCDD_ROOT", os.path.dirname(os.path.abspath(__file__))), "data", "incoming")
SETS = {  # name -> Mendeley dataset id
    "okra":       "nh7zk4hv8z",
    "chilli_bd":  "w9mr3vf56s",
    "chilli_in1": "tm3v4zmh7c",
    "chilli_in2": "ymt8k9bjkn",
}
API = "https://data.mendeley.com/public-api/datasets/%s"
UA = {"User-Agent": "Mozilla/5.0 (OCDD downloader)"}

def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)

def fetch(url, dest):
    for attempt in range(1, 6):
        try: return fetch_once(url, dest)
        except Exception as e:
            print('  attempt %d failed: %s' % (attempt, e), flush=True)
            if attempt == 5: raise
            time.sleep(10 * attempt)

def fetch_once(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print("  have", os.path.basename(dest)); return
    tmp = dest + ".part"
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as f:
        total = int(r.headers.get("Content-Length") or 0); got = 0; t = time.time()
        while True:
            b = r.read(1 << 20)
            if not b: break
            f.write(b); got += len(b)
            if time.time() - t > 5:
                t = time.time(); print("   %.0f MB%s" % (got/1e6, " / %.0f MB" % (total/1e6) if total else ""), flush=True)
    os.replace(tmp, dest)

def entries(x):
    if isinstance(x, dict):
        for k in ("files", "items", "data", "results"):
            if isinstance(x.get(k), list): return x[k]
        return []
    return [e for e in x if isinstance(e, dict)] if isinstance(x, list) else []

def walk(did, meta, folder=None, depth=0):
    out = []
    tries = []
    if folder is None and entries(meta): tries.append(entries(meta))
    for url in ([API % did + "/files?folder_id=%s" % (folder or "root"),
                 API % did + "/files?folder_id=%s&version=1" % (folder or "root")]):
        try: tries.append(entries(get_json(url)))
        except Exception as e: print("  (files call failed: %s)" % e)
    for lst in tries:
        for e in lst:
            cd = e.get("content_details") or {}
            if cd.get("download_url"):
                out.append((e.get("filename") or e.get("name") or e["id"], cd["download_url"]))
            elif e.get("id") and depth < 3 and (e.get("filename") is None or e.get("type") == "folder"):
                out += walk(did, meta, e["id"], depth + 1)
        if out: break
    return out

def one(name, did):
    d = os.path.join(ROOT, name); os.makedirs(d, exist_ok=True)
    print("==", name, did)
    meta = get_json(API % did)
    lic = (meta.get("data_licence") if isinstance(meta.get("data_licence"), dict) else {}).get("full_name") or "UNKNOWN - check the page"
    with open(os.path.join(d, "SOURCE.txt"), "w", encoding="utf-8") as f:
        f.write("Title: %s\nLink: https://data.mendeley.com/datasets/%s\nLicence: %s\n" % (meta.get("name"), did, lic))
    print("  licence:", lic)
    files = walk(did, meta)
    if not files:
        print("  API gave:", str(meta)[:300])
        print("  no files listed - download manually from the link in SOURCE.txt"); return
    for fn, url in files:
        fn = os.path.basename(fn)
        dest = os.path.join(d, fn)
        mark = os.path.join(d, ".done_" + fn)
        if os.path.exists(mark):
            print("  already done", fn); continue
        try: fetch(url, dest)
        except Exception as e:
            print('  GAVE UP on %s: %s - re-run later or download it by hand' % (fn, e)); continue
        if fn.lower().endswith(".zip"):
            print("  unzipping", fn, "->", d)
            with zipfile.ZipFile(dest) as z:
                bad = z.testzip()
                if bad: raise RuntimeError("corrupt zip member %s - delete %s and re-run" % (bad, fn))
                z.extractall(d)
            os.remove(dest)          # only reached if the unzip fully succeeded
            print("  deleted", fn)
        open(mark, "w").close()

if __name__ == "__main__":
    want = sys.argv[1:] or list(SETS)
    for n in want:
        try: one(n, SETS[n])
        except Exception as e: print("  FAILED %s: %s  (download it manually, see SOURCE.txt link)" % (n, e))
    print("\nDone. Still manual: IP102 (pests) and rice - see below.")
    print(" IP102: https://github.com/xpwu95/IP102  (links in its README) -> data\\incoming\\ip102")
    print(" Rice : search 'Dhan-Shomadhan' on data.mendeley.com / Kaggle -> data\\incoming\\rice_dhan")
