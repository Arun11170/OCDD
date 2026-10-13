"""Crop Advisor core for the Raspberry Pi 5 (and any PC): photo -> disease/pest answer -> advice.

Uses exactly the same files and rules as the Android app, so both give the same answer:
  vision.onnx (photo model), classes.json, gates.json, kb.json (CIB&RC-checked advice, no doses), i18n/<lang>.json (translations),
  severity.py (plain OpenCV damage estimate).
"""
import hashlib, json, os, sys, time
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.environ.get("OCDD_ASSETS", os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "assets"))
sys.path.insert(0, ROOT)

GATE = 0.80                 # below this the answer is "not sure"
PEST_HEALTHY_MAX = 0.03     # a pest answer is refused when the model also thinks the leaf may be healthy

LANGS = {"en": "English", "hi": "हिन्दी", "bn": "বাংলা", "te": "తెలుగు", "mr": "मराठी", "ta": "தமிழ்", "gu": "ગુજરાતી",
         "ur": "اردو", "kn": "ಕನ್ನಡ", "or": "ଓଡ଼ିଆ", "ml": "മലയാളം", "pa": "ਪੰਜਾਬੀ", "as": "অসমীয়া"}
SPEECH = {"en": "en-IN", "hi": "hi-IN", "bn": "bn-IN", "te": "te-IN", "mr": "mr-IN", "ta": "ta-IN", "gu": "gu-IN",
          "ur": "ur-IN", "kn": "kn-IN", "or": "or-IN", "ml": "ml-IN", "pa": "pa-IN", "as": "as-IN"}


def _key(en):
    return hashlib.sha1(en.strip().encode("utf-8")).hexdigest()[:12]


class Advisor:
    def __init__(self, threads=4):
        import onnxruntime as ort
        so = ort.SessionOptions(); so.intra_op_num_threads = threads
        self.sess = ort.InferenceSession(os.path.join(ASSETS, "vision.onnx"), sess_options=so, providers=["CPUExecutionProvider"])
        self.inp = self.sess.get_inputs()[0].name
        cl = json.load(open(os.path.join(ASSETS, "classes.json"), encoding="utf-8"))
        self.classes = cl if isinstance(cl, list) else [cl[k] for k in sorted(cl, key=int)]
        self.healthy_idx = [i for i, c in enumerate(self.classes) if "healthy" in c]
        try:
            self.gates = {k: max(GATE, min(0.95, float(v))) for k, v in json.load(open(os.path.join(ASSETS, "gates.json"))).items()}
        except Exception:
            self.gates = {}
        kb = json.load(open(os.path.join(ASSETS, "kb.json"), encoding="utf-8"))["entries"]
        self.kb = {e["class_id"]: e for e in kb}
        self._i18n = {}

    # ---------- translations (same hashing as I18n.kt) ----------
    def t(self, lang, en):
        if lang == "en" or not en:
            return en
        if lang not in self._i18n:
            try:
                self._i18n[lang] = json.load(open(os.path.join(ASSETS, "i18n", lang + ".json"), encoding="utf-8"))
            except Exception:
                self._i18n[lang] = {}
        return self._i18n[lang].get(_key(en), en)

    # ---------- the photo model ----------
    @staticmethod
    def _tensor(im):
        w, h = im.size; s = 256 / min(w, h)
        im = im.resize((max(224, round(w * s)), max(224, round(h * s))), Image.BILINEAR)
        w, h = im.size; x0 = (w - 224) // 2; y0 = (h - 224) // 2
        im = im.crop((x0, y0, x0 + 224, y0 + 224))
        return np.asarray(im, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0

    def gate_for(self, class_id):
        return max(GATE, self.gates.get(class_id.split("__")[0], GATE))

    def answers(self, class_id, prob, healthy_mass):
        return prob >= self.gate_for(class_id) and not (class_id.startswith("pest__") and healthy_mass >= PEST_HEALTHY_MAX)

    def predict(self, im):
        t0 = time.time()
        z = self.sess.run(None, {self.inp: self._tensor(im.convert("RGB"))})[0][0]
        p = np.exp(z - z.max()); p /= p.sum()
        order = np.argsort(-p)[:6]
        ranked = [(self.classes[i], float(p[i])) for i in order]
        hm = float(p[self.healthy_idx].sum())
        top, prob = ranked[0]
        return {"ranked": ranked, "top": top, "prob": prob, "healthy_mass": hm, "sure": self.answers(top, prob, hm), "ms": int((time.time() - t0) * 1000)}

    # ---------- damage estimate ----------
    @staticmethod
    def severity(im):
        try:
            import cv2
            from severity import estimate
            bgr = cv2.cvtColor(np.asarray(im.convert("RGB")), cv2.COLOR_RGB2BGR)
            r = estimate(bgr)
            if not r.get("ok"):
                return {"ok": False, "reason": r.get("reason", "no leaf found")}
            return {"ok": True, "percent": r["severity_pct"], "band": r["band"], "leaf_frac": r["leaf_area_frac"], "warnings": r["warnings"]}
        except Exception as e:
            return {"ok": False, "reason": str(e)}

    # ---------- the whole check ----------
    def check(self, im, lang="en"):
        pred = self.predict(im)
        sev = self.severity(im)
        top = pred["top"]
        e = self.kb.get(top)
        healthy = bool(e and e.get("type") == "healthy") or "healthy" in top
        out = {"class_id": top, "confidence": pred["prob"], "sure": pred["sure"], "healthy": healthy and pred["sure"], "ms": pred["ms"],
               "possibilities": [{"class_id": c, "name": self.t(lang, self.kb[c]["name_en"]) if c in self.kb else c, "prob": p} for c, p in pred["ranked"][:3]],
               "severity": sev if (sev.get("ok") and pred["sure"] and not healthy and sev["percent"] >= 0.5) else None}
        if e and pred["sure"]:
            out["advice"] = self.advice(e, lang)
        return out

    def advice(self, e, lang):
        t = lambda s: self.t(lang, s)
        return {
            "name": t(e["name_en"]), "crop": e.get("crop", ""), "type": e.get("type", ""), "cause": t(e.get("cause", "")),
            "symptoms": [t(s) for s in e.get("symptoms", [])],
            "look_alikes": t(e["look_alikes"]) if e.get("look_alikes") else None,
            "actions": [t(s) for s in e.get("cultural_actions", [])],
            "treatments": [{"kind": x.get("kind", ""), "what": x.get("active_ingredient", ""), "status": x.get("cibrc_status", "unchecked")} for x in e.get("treatment_options", [])],
            "when_to_act": t(e["when_to_act"]) if e.get("when_to_act") else None,
            "ask": [{"q": t(a.get("question", "")), "yes": a.get("if_yes", ""), "no": a.get("if_no", "")} for a in e.get("ask_back", [])],
            "caveats": t(e["caveats"]) if e.get("caveats") else None,
        }
