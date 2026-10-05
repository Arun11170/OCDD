"""Download IP102 pests + extra crop datasets from Kaggle, unpack everything (nested zips too), delete the zips, audit all raw datasets.
Touches ONLY D:\\OCDD\\data\\raw\\ip102, D:\\OCDD\\data\\raw\\_dl and writes D:\\OCDD\\data\\raw_audit.txt"""
import os, sys, shutil, subprocess, zipfile, tarfile

ROOT = r"D:\OCDD"
RAW = os.path.join(ROOT, "data", "raw")
DL = os.path.join(RAW, "_dl")
IP = os.path.join(RAW, "ip102")
# Kaggle names are from memory and NOT verified; any that fail are skipped and listed.
DATASETS = {
    "ip102": "rtlmhjbn/ip02-dataset",                                   # 102 pest classes, many crops
    "plantvillage_all": "abdallahalidev/plantvillage-dataset",          # 14 crops, 38 classes (lab photos)
    "mango": "aryashah2k/mango-leaf-disease-dataset",
    "sugarcane": "nirmalsankalana/sugarcane-leaf-disease-dataset",
    "maize": "smaranjitghose/corn-or-maize-leaf-disease-dataset",
    "cotton": "seroshkarim/cotton-leaf-infection",
    "wheat": "olyadgetch/wheat-leaf-dataset",
    "banana": "sujaykapadnis/banana-disease-recognition-dataset",
}
EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
out = []
def say(s=""):
    print(s); out.append(s)

def pip(pkg):
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", pkg])

def free_gb(p):
    return shutil.disk_usage(p).free / 1e9

def unpack_all(folder):
    """extract every archive under folder (repeat for nested ones), delete each archive after success"""
    n = 0
    while True:
        found = [os.path.join(dp, f) for dp, _, fs in os.walk(folder) for f in fs
                 if f.lower().endswith((".zip", ".tar", ".tar.gz", ".tgz"))]
        if not found:
            return n
        progress = False
        for a in found:
            dest = os.path.splitext(a)[0] if a.lower().endswith(".zip") else a + "_x"
            say("  extracting " + os.path.basename(a))
            try:
                if a.lower().endswith(".zip"):
                    with zipfile.ZipFile(a) as z: z.extractall(dest)
                else:
                    with tarfile.open(a) as t: t.extractall(dest)
                os.remove(a); n += 1; progress = True
            except Exception as e:
                say("  FAILED %s: %s (left in place)" % (a, e))
        if not progress:
            return n

def count_images(folder):
    return sum(1 for dp, _, fs in os.walk(folder) for f in fs if os.path.splitext(f)[1].lower() in EXT)

def audit():
    say("\n===== AUDIT of data\\raw =====")
    for name in sorted(os.listdir(RAW)):
        p = os.path.join(RAW, name)
        if not os.path.isdir(p) or name == "_dl": continue
        imgs = count_images(p)
        arch = [f for dp, _, fs in os.walk(p) for f in fs if f.lower().endswith((".zip", ".tar", ".gz", ".tgz"))]
        leaf = {}
        for dp, _, fs in os.walk(p):
            c = sum(1 for f in fs if os.path.splitext(f)[1].lower() in EXT)
            if c: leaf[dp] = c
        say("%-22s images=%-7d image-folders=%-4d leftover archives=%d  %s" % (
            name, imgs, len(leaf), len(arch), "" if imgs else "<-- NO IMAGES FOUND"))
        for dp, c in sorted(leaf.items(), key=lambda x: -x[1])[:6]:
            say("      %6d  %s" % (c, os.path.relpath(dp, p)))

def main():
    os.makedirs(RAW, exist_ok=True)
    say("free space on D: %.1f GB" % free_gb(ROOT))
    if free_gb(ROOT) < 25:
        say("Less than 25 GB free. Free some space first, then run again."); return 1
    tok = os.path.join(os.path.expanduser("~"), ".kaggle", "kaggle.json")
    if not os.path.exists(tok) and not os.path.exists(os.path.join(os.path.dirname(tok), "access_token")) and not os.environ.get("KAGGLE_API_TOKEN") and not (os.environ.get("KAGGLE_USERNAME") and os.environ.get("KAGGLE_KEY")):
        say("""
Kaggle login file missing. One-time setup (2 minutes):
  1. Open kaggle.com, sign in (free).  2. Profile picture > Settings > API > Create New Token.
  3. A file kaggle.json downloads. Put it in:  %s
  4. Run this again.""" % os.path.dirname(tok))
        audit(); open(os.path.join(ROOT, "data", "raw_audit.txt"), "w", encoding="utf-8").write("\n".join(out)); return 2
    try: import kaggle  # noqa
    except Exception: pip("kaggle")
    failed = []
    for name, slug in DATASETS.items():
        dest = os.path.join(RAW, name)
        if count_images(dest) >= 500:
            say("[skip] %s already there" % name); continue
        if free_gb(ROOT) < 15:
            say("[stop] under 15 GB free, not downloading more"); break
        tmp = os.path.join(DL, name); os.makedirs(tmp, exist_ok=True)
        say("[get ] %s  (%s) ..." % (name, slug))
        r = subprocess.call([sys.executable, "-m", "kaggle", "datasets", "download", "-d", slug, "-p", tmp])
        if r != 0:
            say("[FAIL] %s: could not download (wrong name, not accepted on Kaggle, or no internet). Continuing." % name)
            failed.append(name); shutil.rmtree(tmp, ignore_errors=True); continue
        os.makedirs(dest, exist_ok=True)
        for f in os.listdir(tmp):
            if f.lower().endswith(".zip"):
                try:
                    with zipfile.ZipFile(os.path.join(tmp, f)) as z: z.extractall(dest)
                    os.remove(os.path.join(tmp, f))
                except Exception as e:
                    say("[FAIL] unpack %s: %s" % (f, e)); failed.append(name)
        shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(DL, ignore_errors=True)
    if failed: say("\nFailed downloads: " + ", ".join(failed))
    say("checking for nested archives ...")
    say("  unpacked %d nested archive(s)" % unpack_all(RAW))
    audit()
    open(os.path.join(ROOT, "data", "raw_audit.txt"), "w", encoding="utf-8").write("\n".join(out))
    say("\nSaved report: D:\\OCDD\\data\\raw_audit.txt  (send me this file)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
