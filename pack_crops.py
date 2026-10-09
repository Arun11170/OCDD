"""Copies each crop specialist into the app (assets\\crops\\<crop>.onnx + .json).
Uses the small int8 file when it is within 0.5 points of the exact fp32 file, else fp32. Writes crops_report.txt.
Compare each specialist's test accuracy with the 95-class model on the same crop before shipping it."""
import json, os, re, shutil
ROOT = r"D:\\OCDD"
APP = os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "assets", "crops")
os.makedirs(APP, exist_ok=True)
rep = []
for c in "apple banana capsicum chilli grape groundnut maize mango okra potato rice sugarcane tomato wheat".split():
    d = os.path.join(ROOT, "export_crops", c)
    r = os.path.join(d, "onnx_report.txt")
    if not os.path.isfile(r):
        rep.append("%-10s not exported" % c); continue
    txt = open(r).read()
    acc = {m.group(1): float(m.group(2)) for m in re.finditer(r"(vision_\w+\.onnx)\s+[\d.]+ MB\s+accuracy on \d+ test photos: ([\d.]+)", txt)}
    pick = "vision_int8.onnx" if acc.get("vision_int8.onnx", 0) >= acc.get("vision_fp32.onnx", 1) - 0.005 else "vision_fp32.onnx"
    shutil.copy(os.path.join(d, pick), os.path.join(APP, c + ".onnx"))
    shutil.copy(os.path.join(ROOT, "training", "crop_" + c, "classes.json"), os.path.join(APP, c + ".json"))
    rep.append("%-10s %-18s acc fp32 %.3f int8 %.3f  (%d classes incl. pests)" % (
        c, pick, acc.get("vision_fp32.onnx", 0), acc.get("vision_int8.onnx", 0), len(json.load(open(os.path.join(ROOT, "training", "crop_" + c, "classes.json"))))))
open(os.path.join(ROOT, "crops_report.txt"), "w").write("\n".join(rep))
print("\n".join(rep))
print("\nCompare each accuracy with the general model's accuracy on the same crop before shipping. Remove assets\\crops\\<crop>.onnx for any crop where it is not better.")
