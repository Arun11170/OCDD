"""Local web app: open http://<pi-address>:8000 on the Pi's own screen or on any phone/laptop on the same Wi-Fi (no internet needed).

  python pi5/server.py            (port 8000)       Take photo = the Pi's camera.  Upload / phone camera = any browser.
Photos and results stay on the Pi (folder ~/ocdd_data). Nothing is sent anywhere.
"""
import html, io, json, os, sys, time, threading
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flask import Flask, Response, request, send_file, redirect
from PIL import Image, ImageOps
from advisor import Advisor, LANGS, SPEECH
import camera

DATA = os.path.expanduser(os.environ.get("OCDD_DATA", "~/ocdd_data")); os.makedirs(DATA, exist_ok=True)
app = Flask(__name__)
adv = Advisor()
lock = threading.Lock()          # one check at a time: the Pi has 4 cores

UI = {
    "title": ("Crop Advisor", "फसल सलाहकार"),
    "take": ("Take photo with the Pi camera", "Pi कैमरे से फोटो लें"),
    "upload": ("Choose or take a photo here", "यहाँ से फोटो चुनें या खींचें"),
    "tag": ("Photo a leaf or pest. Works offline.", "पत्ती या कीट की फोटो लें। बिना इंटरनेट चलता है।"),
    "sure": ("Most likely", "सबसे संभावित"), "unsure": ("Not sure about this photo", "इस फोटो के बारे में पक्का नहीं"),
    "closest": ("Closest matches", "सबसे नज़दीकी"), "damage": ("Estimated damage", "अनुमानित नुकसान"),
    "see": ("What you may see", "क्या दिख सकता है"), "do": ("What to do", "क्या करें"),
    "spray": ("Spray options (follow the product label; no dose here)", "स्प्रे विकल्प (उत्पाद के लेबल का पालन करें; यहाँ मात्रा नहीं)"),
    "look": ("Looks similar to", "इनसे मिलता-जुलता"), "when": ("When to act", "कब कार्रवाई करें"),
    "retake": ("Try again: a closer, sharper photo of one leaf in daylight.", "फिर कोशिश करें: दिन की रोशनी में एक पत्ती की पास से साफ फोटो।"),
    "listen": ("Read aloud", "पढ़कर सुनाएँ"), "again": ("New check", "नई जाँच"), "history": ("History", "इतिहास"),
    "healthy": ("Healthy", "स्वस्थ"), "expert": ("Not a cure-all: if the damage is spreading, ask your KVK (Kisan Call Centre 1800-180-1551).", "यह हर बात का इलाज नहीं: नुकसान फैल रहा हो तो अपने KVK से पूछें (किसान कॉल सेंटर 1800-180-1551)।"),
}


def u(lang, k):
    en, hi = UI[k]
    return hi if lang == "hi" else (adv.t(lang, en) if lang != "en" else en)


CSS = """
:root{--g:#1B8A4B;--f:#0B3B2A;--m:#E4F3EA;--bg:#F4F6F1;--ink:#14231B;--mu:#5B6B62}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:17px/1.45 system-ui,Segoe UI,Roboto,sans-serif}
.w{max-width:760px;margin:0 auto;padding:16px}header{display:flex;align-items:center;gap:10px;padding:6px 0 14px}
h1{font-size:22px;margin:0;flex:1;color:var(--f)}select{font:inherit;padding:8px 10px;border-radius:12px;border:1px solid #cfd8d2;background:#fff}
.hero{background:linear-gradient(135deg,var(--f),var(--g));color:#fff;border-radius:26px;padding:22px;margin-bottom:14px}
.hero h2{margin:0 0 4px;font-size:26px}.hero p{margin:0 0 16px;opacity:.85;font-size:15px}
.btn{display:block;width:100%;border:0;border-radius:16px;padding:16px;font:600 18px system-ui;margin:8px 0;text-align:center;cursor:pointer;text-decoration:none}
.b1{background:#fff;color:var(--f)}.b2{background:var(--m);color:var(--f)}.b3{background:var(--g);color:#fff}
.card{background:#fff;border-radius:22px;padding:18px;margin:12px 0;box-shadow:0 1px 3px rgba(0,0,0,.06)}
.big{font-size:26px;font-weight:800;margin:0}.sub{color:var(--mu);margin:2px 0 0}
img.p{width:100%;border-radius:20px;display:block}details{background:#fff;border-radius:18px;padding:4px 16px;margin:8px 0}
summary{font-weight:700;padding:12px 0;cursor:pointer}li{margin:6px 0}.mu{color:var(--mu);font-size:14px}
.bar{height:10px;border-radius:6px;background:#e6ebe7;overflow:hidden;margin-top:8px}.bar i{display:block;height:100%}
.pill{display:inline-block;background:var(--m);color:var(--f);border-radius:50px;padding:4px 12px;font-size:14px;font-weight:700}
"""


def page(lang, body, title=None):
    opts = "".join('<option value="%s"%s>%s</option>' % (c, " selected" if c == lang else "", n) for c, n in LANGS.items())
    return Response("""<!doctype html><html lang="%s"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>%s</title><style>%s</style></head><body><div class="w"><header><h1>🌿 %s</h1>
<select onchange="location='/?lang='+this.value">%s</select></header>%s</div></body></html>""" % (lang, html.escape(title or u(lang, "title")), CSS, html.escape(u(lang, "title")), opts, body), mimetype="text/html")


def home(lang, note=""):
    body = """%s<div class="hero"><h2>%s</h2><p>%s</p>
<form method="post" action="/capture?lang=%s"><button class="btn b1" type="submit">📷 %s</button></form>
<form method="post" action="/analyze?lang=%s" enctype="multipart/form-data"><label class="btn b2">🖼 %s
<input type="file" name="photo" accept="image/*" capture="environment" hidden onchange="this.form.submit()"></label></form></div>
<a class="btn b2" href="/history?lang=%s">🕘 %s</a>""" % (("<div class='card'>%s</div>" % html.escape(note)) if note else "", html.escape(u(lang, "title")), html.escape(u(lang, "tag")),
                                                           lang, html.escape(u(lang, "take")), lang, html.escape(u(lang, "upload")), lang, html.escape(u(lang, "history")))
    return page(lang, body)


def colour(r):
    if r["healthy"]:
        return "#DDF2E3", "#1B8A4B"
    c = max(0.0, min(1.0, (r["confidence"] - 0.5) / 0.5))                   # yellow (unsure) -> red (very sure it is a problem)
    mix = lambda a, b: int(a + (b - a) * c)
    return "#%02X%02X%02X" % (mix(255, 255), mix(236, 205), mix(179, 210)), "#%02X%02X%02X" % (mix(230, 198), mix(160, 40), mix(0, 40))


def result_page(lang, pid, r):
    bg, fg = colour(r) if r["sure"] else ("#FFF3D6", "#B26A00")
    a = r.get("advice")
    out = ['<img class="p" src="/photo/%s">' % pid]
    if r["sure"] and a:
        out.append('<div class="card" style="background:%s"><p class="big">%s</p><p class="sub">%s · %.0f%%</p><div class="bar"><i style="width:%.0f%%;background:%s"></i></div></div>'
                   % (bg, html.escape(a["name"]), html.escape(u(lang, "healthy") if r["healthy"] else a["crop"].title()), 100 * r["confidence"], 100 * r["confidence"], fg))
        if r["severity"]:
            out.append('<div class="card"><b>%s</b><p class="sub"><span class="pill">%s</span> ~%s%%</p></div>' % (html.escape(u(lang, "damage")), html.escape(r["severity"]["band"]), r["severity"]["percent"]))
        speak = [a["name"]] + a["actions"][:3]
        out.append('<button class="btn b3" onclick="say()">🔊 %s</button>' % html.escape(u(lang, "listen")))
        if a["symptoms"]:
            out.append("<details><summary>%s</summary><ul>%s</ul></details>" % (html.escape(u(lang, "see")), "".join("<li>%s</li>" % html.escape(s) for s in a["symptoms"])))
        if a["actions"]:
            out.append("<details open><summary>%s</summary><ul>%s</ul></details>" % (html.escape(u(lang, "do")), "".join("<li>%s</li>" % html.escape(s) for s in a["actions"])))
        if a["treatments"]:
            out.append("<details><summary>%s</summary><ul>%s</ul></details>" % (html.escape(u(lang, "spray")), "".join("<li>%s <span class='mu'>(%s)</span></li>" % (html.escape(x["what"]), html.escape(x["kind"])) for x in a["treatments"])))
        if a["when_to_act"]:
            out.append("<details><summary>%s</summary><p>%s</p></details>" % (html.escape(u(lang, "when")), html.escape(a["when_to_act"])))
        if a["look_alikes"]:
            out.append("<details><summary>%s</summary><p>%s</p></details>" % (html.escape(u(lang, "look")), html.escape(a["look_alikes"])))
        out.append('<p class="mu">%s</p>' % html.escape(u(lang, "expert")))
        out.append("<script>function say(){speechSynthesis.cancel();var u=new SpeechSynthesisUtterance(%s);u.lang='%s';speechSynthesis.speak(u)}</script>" % (json.dumps(". ".join(speak)), SPEECH.get(lang, "en-IN")))
    else:
        out.append('<div class="card" style="background:#FFF3D6"><p class="big">%s</p><p class="sub">%s</p></div>' % (html.escape(u(lang, "unsure")), html.escape(u(lang, "retake"))))
        out.append("<details open><summary>%s</summary><ul>%s</ul></details>" % (html.escape(u(lang, "closest")), "".join("<li>%s <span class='mu'>%.0f%%</span></li>" % (html.escape(p["name"]), 100 * p["prob"]) for p in r["possibilities"])))
    out.append('<a class="btn b2" href="/?lang=%s">📷 %s</a>' % (lang, html.escape(u(lang, "again"))))
    return page(lang, "".join(out))


def process(im, lang):
    im = ImageOps.exif_transpose(im).convert("RGB")
    pid = time.strftime("%Y%m%d_%H%M%S")
    im.thumbnail((1600, 1600)); im.save(os.path.join(DATA, pid + ".jpg"), quality=90)
    with lock:
        r = adv.check(im, lang)
    with open(os.path.join(DATA, "history.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps({"id": pid, "class_id": r["class_id"], "confidence": round(r["confidence"], 3), "sure": r["sure"], "severity": (r["severity"] or {}).get("percent")}) + "\n")
    return result_page(lang, pid, r)


def L():
    lang = request.args.get("lang", "en"); return lang if lang in LANGS else "en"


@app.route("/")
def index():
    return home(L())


@app.route("/capture", methods=["POST"])
def capture_route():
    lang = L()
    try:
        with lock:
            im = camera.capture()
    except Exception as e:
        return home(lang, str(e))
    return process(im, lang)


@app.route("/analyze", methods=["POST"])
def analyze_route():
    lang = L()
    f = request.files.get("photo")
    if not f:
        return redirect("/?lang=" + lang)
    try:
        return process(Image.open(io.BytesIO(f.read())), lang)
    except Exception as e:
        return home(lang, "Could not read that photo: %s" % e)


@app.route("/photo/<pid>")
def photo(pid):
    p = os.path.join(DATA, os.path.basename(pid) + ".jpg")
    return send_file(p, mimetype="image/jpeg") if os.path.isfile(p) else ("", 404)


@app.route("/history")
def history():
    lang = L(); rows = []
    p = os.path.join(DATA, "history.jsonl")
    if os.path.isfile(p):
        for line in open(p, encoding="utf-8").read().splitlines()[-40:][::-1]:
            h = json.loads(line); e = adv.kb.get(h["class_id"], {})
            name = adv.t(lang, e.get("name_en", h["class_id"])) if h["sure"] else "—"
            rows.append('<div class="card"><b>%s</b> <span class="mu">%s · %.0f%%</span></div>' % (html.escape(name), h["id"], 100 * h["confidence"]))
    return page(lang, "".join(rows) or "<div class='card'>—</div>")


if __name__ == "__main__":
    print("Open http://localhost:8000  (or http://<this Pi's address>:8000 from a phone on the same Wi-Fi)")
    app.run(host="0.0.0.0", port=8000, threaded=True)
