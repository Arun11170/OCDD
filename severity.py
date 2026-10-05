"""Leaf damage (severity %) estimate with plain OpenCV. No ML, so it is fast and runs on any phone.

severity % = damaged leaf area / whole leaf area (damaged = inside the leaf outline but not healthy green).
The same steps are ported to Kotlin with the OpenCV Android library (see steps in comments).

Usage:  python severity.py photo.jpg
"""
import sys
import cv2
import numpy as np

BANDS = [(5, "healthy / very light"), (15, "mild"), (35, "moderate"), (101, "severe")]


def band(pct):
    for limit, name in BANDS:
        if pct < limit:
            return name
    return BANDS[-1][1]


def estimate(bgr, max_side=512):
    h, w = bgr.shape[:2]
    s = max_side / max(h, w)
    if s < 1:
        bgr = cv2.resize(bgr, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
    bgr = cv2.GaussianBlur(bgr, (5, 5), 0)
    H, W = bgr.shape[:2]
    b, g, r = [c.astype(np.float32) for c in cv2.split(bgr)]
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    hue, sat, val = [c.astype(np.float32) for c in cv2.split(hsv)]

    # 1. healthy green: green dominates AND hue in the green band (OpenCV hue 0..180, green ~35-85)
    exg = 2 * g - r - b
    healthy = ((exg > 18) & (hue >= 33) & (hue <= 90) & (sat > 40) & (val > 40)).astype(np.uint8)

    # 2. leaf outline: biggest green blob, joined across small gaps, holes filled
    k = max(5, int(0.04 * max(H, W)) | 1)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
    closed = cv2.morphologyEx(healthy, cv2.MORPH_CLOSE, kernel)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(closed, connectivity=8)
    if n <= 1:
        return dict(ok=False, reason="no leaf found: move closer, fill the frame with one leaf")
    # keep every blob that is at least 15% as big as the largest (compound leaves, several leaflets)
    areas = stats[1:, cv2.CC_STAT_AREA]
    keep = [i + 1 for i, a in enumerate(areas) if a >= 0.15 * areas.max()]
    outline = np.isin(lab, keep).astype(np.uint8)
    cnts, _ = cv2.findContours(outline, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    filled = np.zeros_like(outline)
    cv2.drawContours(filled, cnts, -1, 1, thickness=cv2.FILLED)   # fills the holes where lesions are

    # 3. damage = inside outline but not healthy green; drop specks (noise) and thin edge halos
    damaged = ((filled == 1) & (healthy == 0)).astype(np.uint8)
    damaged = cv2.morphologyEx(damaged, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3)))

    leaf_area = int(filled.sum())
    dmg_area = int(damaged.sum())
    leaf_frac = leaf_area / float(H * W)
    pct = 100.0 * dmg_area / max(leaf_area, 1)

    warnings = []
    if leaf_frac < 0.08:
        warnings.append("leaf is small in the photo: take it closer")
    if leaf_frac > 0.97:
        warnings.append("leaf fills the whole photo: edges may be cut off")
    return dict(ok=True, severity_pct=round(pct, 1), band=band(pct), leaf_area_frac=round(leaf_frac, 3),
                warnings=warnings, mask_leaf=filled, mask_damage=damaged, small_image=bgr)


def overlay(res):
    img = res["small_image"].copy()
    cnts, _ = cv2.findContours(res["mask_leaf"], cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(img, cnts, -1, (255, 200, 0), 2)
    red = np.zeros_like(img)
    red[res["mask_damage"] == 1] = (0, 0, 255)
    img = cv2.addWeighted(img, 1.0, red, 0.55, 0)
    return img


if __name__ == "__main__":
    im = cv2.imread(sys.argv[1])
    if im is None:
        sys.exit("cannot open image")
    r = estimate(im)
    if not r["ok"]:
        sys.exit(r["reason"])
    print({k: v for k, v in r.items() if not k.startswith("mask") and k != "small_image"})
    cv2.imwrite("severity_overlay.jpg", overlay(r))
