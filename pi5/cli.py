"""Command line check (works with no screen or camera).

  python pi5/cli.py photo.jpg [more.jpg ...] [--lang hi]
  python pi5/cli.py --selftest          runs the 13 test photos in phone_test_photos/ and compares with WHAT_TO_EXPECT.txt
"""
import argparse, glob, os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from advisor import Advisor, ROOT


def show(path, r, lang):
    print("\n%s" % os.path.basename(path))
    top = r["possibilities"][0]
    if r["sure"]:
        a = r.get("advice") or {}
        print("  %s  %.0f%%  (%d ms)%s" % (a.get("name", top["name"]), 100 * r["confidence"], r["ms"], "  HEALTHY" if r["healthy"] else ""))
        if r["severity"]:
            print("  damage about %s%% of the leaf (%s)" % (r["severity"]["percent"], r["severity"]["band"]))
        for s in a.get("actions", [])[:3]:
            print("   -", s)
        if a.get("treatments"):
            print("   spray options (follow the product label, no dose here):", "; ".join(x["what"] for x in a["treatments"][:3]))
    else:
        print("  NOT SURE (%.0f%%). Closest: %s  (%d ms)" % (100 * r["confidence"], ", ".join("%s %.0f%%" % (p["name"], 100 * p["prob"]) for p in r["possibilities"]), r["ms"]))


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("photos", nargs="*"); ap.add_argument("--lang", default="en"); ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    adv = Advisor()
    paths = a.photos
    if a.selftest:
        paths = sorted(glob.glob(os.path.join(ROOT, "phone_test_photos", "*.jpg")) + glob.glob(os.path.join(ROOT, "phone_test_photos", "*.png")))
        print(open(os.path.join(ROOT, "phone_test_photos", "WHAT_TO_EXPECT.txt"), encoding="utf-8").read())
    if not paths:
        sys.exit("give a photo, or --selftest")
    t0 = time.time()
    for p in paths:
        show(p, adv.check(Image.open(p), a.lang), a.lang)
    print("\n%d photos in %.1f s" % (len(paths), time.time() - t0))


if __name__ == "__main__":
    main()
