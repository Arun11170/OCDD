"""
Checks voice/typed command understanding on the PC, with the SAME rules as the app (VoiceNav.kt and Embedder.kt are read directly,
so there is nothing to keep in sync). Prints a pass rate per language, for the word lists alone and with the meaning model added.

    python voice_test\\test_voice.py            (word lists + meaning model if models\\voice_ai exists)
Add your own lines to voice_phrases.tsv:  LANG <tab> COMMAND <tab> what a farmer would say.
"""
import os, re, sys, unicodedata, collections
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "app", "CropAdvisor", "app", "src", "main", "java", "com", "cropadvisor")
nav = open(os.path.join(SRC, "VoiceNav.kt"), encoding="utf-8").read()
emb = open(os.path.join(SRC, "Embedder.kt"), encoding="utf-8").read()

ORDER = re.search(r"private val order = listOf\((.*?)\)", nav).group(1)
ORDER = [x.strip().split(".")[1] for x in ORDER.split(",")]
TABLE = re.search(r'TABLE = """(.*?)"""', nav, re.S).group(1)


def norm(t):
    return unicodedata.normalize("NFC", t).lower().replace("‍", "").replace("‌", "")


words = collections.defaultdict(lambda: collections.defaultdict(list))
for line in TABLE.splitlines():
    p = line.strip().split(" ", 2)
    if len(p) == 3:
        words[p[0]][p[1]] += [norm(w.strip()) for w in p[2].split(",") if w.strip()]


def edit(a, b, limit):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        if min(cur) > limit:
            return limit + 1
        prev = cur
    return prev[-1]


def is_ascii_letters(w):
    return all("a" <= ch <= "z" for ch in w)


def fuzzy(lang, t):
    toks, cur = [], ""
    for ch in t:
        if unicodedata.category(ch)[0] in "LM":
            cur += ch
        else:
            if cur: toks.append(cur)
            cur = ""
    if cur: toks.append(cur)
    toks = [x for x in toks if len(x) >= 3]
    for c in ORDER:
        lst = words[lang].get(c, []) + words["EN"].get(c, []) + words["RO"].get(c, [])
        for w in lst:
            if len(w) < (6 if is_ascii_letters(w) else 4):
                continue
            allowed = 2 if len(w) >= 7 else 1
            for tok in toks:
                if abs(len(tok) - len(w)) <= allowed and edit(tok, w, allowed) <= allowed:
                    return c
    return None


def longest(lst, t, minlen=0):
    return max([len(w) for w in lst if len(w) >= minlen and w in t] or [0])


def keyword(lang, heard):
    t = norm(heard)
    bc, bl = None, 0
    for c in ORDER:
        hit = max(longest(words[lang].get(c, []), t), longest(words["EN"].get(c, []), t), longest(words["RO"].get(c, []), t, 4))
        if hit > bl: bc, bl = c, hit
    return bc or fuzzy(lang, t)


# ---- meaning model, if present ----
sess = None
try:
    import numpy as np, onnxruntime as ort
    d = os.path.join(ROOT, "models", "voice_ai")
    sess = ort.InferenceSession(os.path.join(d, "embed.onnx"), providers=["CPUExecutionProvider"])
    vocab = [l.rstrip("\n").rsplit("\t", 1) for l in open(os.path.join(d, "embed_vocab.tsv"), encoding="utf-8")]
    pieces, scores = {}, []
    for i, (p, s) in enumerate(vocab):
        pieces.setdefault(p, i); scores.append(float(s))
    maxlen = min(max(len(p) for p, _ in vocab), 40)
    names = [i.name for i in sess.get_inputs()]
except Exception as e:
    print("(meaning model not tested:", e, ")")
    sess = None


def encode(text):
    s = re.sub(r"\s+", " ", unicodedata.normalize("NFKC", text.strip()))
    if not s: return [0, 2]
    s = "▁" + s.replace(" ", "▁"); n = len(s); NEG = float("-inf")
    best = [NEG] * (n + 1); frm = [0] * (n + 1); tid = [0] * (n + 1); best[0] = 0.0
    for i in range(n):
        if best[i] == NEG: continue
        found = False
        for j in range(i + 1, min(n, i + maxlen) + 1):
            k = pieces.get(s[i:j])
            if k is None: continue
            sc = best[i] + scores[k]
            if sc > best[j]: best[j], frm[j], tid[j] = sc, i, k
            found = True
        if not found or best[i + 1] == NEG:
            sc = best[i] - 20.0
            if sc > best[i + 1]: best[i + 1], frm[i + 1], tid[i + 1] = sc, i, 3
    out, p = [], n
    while p > 0: out.append(tid[p]); p = frm[p]
    out.reverse()
    return [0] + out[:30] + [2]


def embed(text):
    ids = np.array([encode("query: " + text)], dtype=np.int64)
    feed = {"input_ids": ids, "attention_mask": np.ones_like(ids)}
    if "token_type_ids" in names: feed["token_type_ids"] = np.zeros_like(ids)
    v = sess.run(None, feed)[0][0].mean(axis=0)
    return v / np.linalg.norm(v)


MIN_COS = float(re.search(r"MIN_COSINE = ([0-9.]+)f", emb).group(1))
MIN_SHARE = float(re.search(r"MIN_SHARE = ([0-9.]+)f", emb).group(1))
ex_vecs, ex_cmd = [], []
if sess:
    ph = []
    for m in re.finditer(r"Cmd\.(\w+) to listOf\((.*?)\)\s*,?\s*\n", emb):
        for q in re.findall(r'"([^"]+)"', m.group(2)): ph.append((m.group(1), q))
    chat = re.search(r"private val chatter = listOf\((.*?)\)", emb, re.S).group(1)
    ph += [(None, q) for q in re.findall(r'"([^"]+)"', chat)]
    seen = set()
    for lang in words:
        for c, ws in words[lang].items():
            for w in ws[:8]:
                if (c, w) not in seen: seen.add((c, w)); ph.append((c, w))
    print("embedding %d example sentences ..." % len(ph), flush=True)
    for c, q in ph: ex_vecs.append(embed(q)); ex_cmd.append(c)
    ex_vecs = np.stack(ex_vecs)


def meaning(heard):
    if not sess: return None
    q = embed(heard); sims = ex_vecs @ q
    per = {}
    for s, c in zip(sims, ex_cmd): per[c] = max(per.get(c, -1), float(s))
    keys = list(per); v = np.array([per[k] for k in keys])
    e = np.exp((v - v.max()) / 0.01); share = e / e.sum(); i = int(v.argmax())
    return (keys[i], float(v[i]), float(share[i])) if keys[i] is not None and v[i] >= MIN_COS and share[i] >= MIN_SHARE else (None, float(v[i]), float(share[i]))


rows = [l.rstrip("\n").split("\t") for l in open(os.path.join(HERE, "voice_phrases.tsv"), encoding="utf-8") if l.strip()]
stat = collections.OrderedDict(); fails = []
for lang, want, phrase in rows:
    k = keyword(lang, phrase)
    m = meaning(phrase)
    final = k or (m[0] if m else None)
    s = stat.setdefault(lang, [0, 0, 0])
    s[0] += 1; s[1] += (k == want); s[2] += (final == want)
    if final != want: fails.append((lang, want, phrase, k, m))
print("\nlanguage   phrases   word lists only   + meaning model")
tot = [0, 0, 0]
for lang, (n, a, b) in stat.items():
    print("%-9s %7d %14d%% %16d%%" % (lang, n, 100 * a // n, 100 * b // n)); tot = [tot[0] + n, tot[1] + a, tot[2] + b]
print("%-9s %7d %14d%% %16d%%" % ("ALL", tot[0], 100 * tot[1] // tot[0], 100 * tot[2] // tot[0]))
if fails:
    print("\nmissed (lang, wanted, phrase, word-list answer, meaning answer):")
    for f in fails: print("  ", f)
