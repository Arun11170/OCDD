"""
One-time helper: fetch the small multilingual model that lets the app understand voice/typed commands in any of its 13 languages,
check it works the same way the app will use it, and add it to the model release's manifest.json.

Run on your PC (it needs Hugging Face, which the build sandbox cannot reach):
    pip install huggingface_hub tokenizers onnxruntime numpy
    python prepare_voice_ai.py
Then upload with publish_voice_ai.ps1.

Model: intfloat/multilingual-e5-small (MIT licence), ONNX int8 build by Xenova, ~118 MB.
"""
import hashlib, json, os, re, sys, unicodedata
import numpy as np

REPO = "Xenova/multilingual-e5-small"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "voice_ai")
MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "llama", "manifest.json")
os.makedirs(OUT, exist_ok=True)

from huggingface_hub import hf_hub_download, list_repo_files

files = list_repo_files(REPO)
onnx_name = next((f for f in ("onnx/model_quantized.onnx", "onnx/model_int8.onnx") if f in files), None)
if onnx_name is None:
    onnx_name = next(f for f in files if f.endswith(".onnx") and ("quant" in f or "int8" in f))
print("model file:", onnx_name)
src_model = hf_hub_download(REPO, onnx_name)
src_tok = hf_hub_download(REPO, "tokenizer.json")

# --- model: copy under the name the app expects ---
model_path = os.path.join(OUT, "embed.onnx")
with open(src_model, "rb") as a, open(model_path, "wb") as b:
    b.write(a.read())

# --- vocabulary: piece<TAB>score, line number = token id ---
tok = json.load(open(src_tok, encoding="utf-8"))
assert tok["model"]["type"] == "Unigram", "expected a SentencePiece unigram tokenizer"
vocab = tok["model"]["vocab"]
vocab_path = os.path.join(OUT, "embed_vocab.tsv")
with open(vocab_path, "w", encoding="utf-8", newline="\n") as f:
    for piece, score in vocab:
        f.write(f"{piece}\t{score}\n")
print("vocabulary pieces:", len(vocab))

# --- the app's tokenizer, ported line for line, checked against the reference tokenizer ---
pieces = {}
scores = []
for i, (p, s) in enumerate(vocab):
    pieces.setdefault(p, i)
    scores.append(s)
max_len = min(max(len(p) for p, _ in vocab), 40)


def app_encode(text):
    norm = re.sub(r"\s+", " ", unicodedata.normalize("NFKC", text.strip()))
    if not norm:
        return [0, 2]
    s = "▁" + norm.replace(" ", "▁")
    n = len(s)
    NEG = float("-inf")
    best = [NEG] * (n + 1); frm = [0] * (n + 1); tid = [0] * (n + 1)
    best[0] = 0.0
    for i in range(n):
        if best[i] == NEG:
            continue
        found = False
        for j in range(i + 1, min(n, i + max_len) + 1):
            k = pieces.get(s[i:j])
            if k is None:
                continue
            sc = best[i] + scores[k]
            if sc > best[j]:
                best[j], frm[j], tid[j] = sc, i, k
            found = True
        if not found or best[i + 1] == NEG:
            sc = best[i] - 20.0
            if sc > best[i + 1]:
                best[i + 1], frm[i + 1], tid[i + 1] = sc, i, 3
    out = []; p = n
    while p > 0:
        out.append(tid[p]); p = frm[p]
    out.reverse()
    out = out[:30]
    return [0] + out + [2]


from tokenizers import Tokenizer
ref = Tokenizer.from_file(src_tok)
SAMPLES = {
    "EN": "take a photo of the leaf", "HI": "पत्ते की फोटो लो", "BN": "পাতার ছবি তোলো", "TE": "ఆకు ఫోటో తీయి",
    "MR": "पानाचा फोटो काढा", "TA": "இலையின் புகைப்படம் எடு", "GU": "પાનનો ફોટો પાડો", "UR": "پتے کی تصویر لو",
    "KN": "ಎಲೆಯ ಫೋಟೋ ತೆಗೆ", "OR": "ପତ୍ରର ଫଟୋ ନିଅ", "ML": "ഇലയുടെ ഫോട്ടോ എടുക്കുക", "PA": "ਪੱਤੇ ਦੀ ਫੋਟੋ ਖਿੱਚੋ", "AS": "পাতৰ ফটো তোলা",
}
same = 0
for lang, text in SAMPLES.items():
    r = ref.encode("query: " + text).ids
    a = app_encode("query: " + text)
    ok = r == a
    same += ok
    print(f"tokenizer {lang}: {'identical' if ok else 'DIFFERENT'}  (app {len(a)} tokens, reference {len(r)})")
print(f"tokenizer identical for {same}/{len(SAMPLES)} languages")

# --- the model itself, run as the app will run it ---
import onnxruntime as ort
sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
names = [i.name for i in sess.get_inputs()]


def embed(text):
    ids = np.array([app_encode("query: " + text)], dtype=np.int64)
    feed = {"input_ids": ids, "attention_mask": np.ones_like(ids)}
    if "token_type_ids" in names:
        feed["token_type_ids"] = np.zeros_like(ids)
    h = sess.run(None, feed)[0][0]
    v = h.mean(axis=0)
    return v / np.linalg.norm(v)


EXEMPLAR = {
    "CAMERA": ["take a photo of the leaf", "open the camera"],
    "WEATHER": ["what is the weather going to be", "will it rain"],
    "CALL": ["call an agriculture expert", "I want to talk to an expert"],
    "DIARY": ["open the season diary", "write a note about my crop"],
    "BACK": ["go back", "go to the home screen"],
    "HELP": ["I need help", "how do I use this app"],
}
ex = {c: np.stack([embed(t) for t in ts]) for c, ts in EXEMPLAR.items()}
TESTS = [("HI", "मुझे पत्ते की फोटो चाहिए", "CAMERA"), ("TA", "இலையின் புகைப்படம் எடு", "CAMERA"), ("TE", "రేపు వర్షం పడుతుందా", "WEATHER"),
         ("BN", "আমি একজন বিশেষজ্ঞের সাথে কথা বলতে চাই", "CALL"), ("ML", "ഡയറിയിൽ കുറിപ്പ് എഴുതണം", "DIARY"),
         ("MR", "मागे जा", "BACK"), ("UR", "مجھے مدد چاہیے", "HELP"), ("EN", "thank you", None)]
right = 0
for lang, text, want in TESTS:
    q = embed(text)
    per = {c: float((m @ q).max()) for c, m in ex.items()}
    top = max(per, key=per.get)
    v = np.array(list(per.values())); e = np.exp((v - v.max()) / 0.01); share = float(e.max() / e.sum())
    good = (top == want) if want else True
    right += good
    print(f"{lang}: '{text}' -> {top}  cosine {per[top]:.3f}  share {share:.2f}  {'ok' if good else 'WRONG'}  (expected {want})")
print(f"model check: {right}/{len(TESTS)} as expected")
print("If cosines for right answers sit well above those of 'thank you', the thresholds in Embedder.kt (0.80 / 0.50) are fine; "
      "otherwise send me this whole output and I will tune them.")


# --- manifest ---
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


m = json.load(open(MANIFEST, encoding="utf-8"))
m["embed"] = {"file": "embed.onnx", "sha256": sha(model_path), "size": os.path.getsize(model_path)}
m["embedVocab"] = {"file": "embed_vocab.tsv", "sha256": sha(vocab_path), "size": os.path.getsize(vocab_path)}
json.dump(m, open(MANIFEST, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("manifest updated:", MANIFEST)
print("Next: .\\publish_voice_ai.ps1")
