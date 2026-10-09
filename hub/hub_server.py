"""Village hub for Crop Advisor (Raspberry Pi 5, or any PC). Offline: it only talks to phones on the local Wi-Fi.

  pip install llama-cpp-python            (needs a Llama 3.2 1B Instruct GGUF file, e.g. Q8_0 or Q4_K_M)
  python hub_server.py --models ./models --gguf ./models/Llama-3.2-1B-Instruct-Q8_0.gguf --port 8000

Serves two things:
  * the model files for the app's first-run download (put manifest.json, tokenizer.model, the .pte files in --models;
    make manifest.json with make_manifest.py) -> phones type  <hub-ip>:8000  as the model server address;
  * POST /ask  {"prompt": "...", "max_tokens": 160}  ->  {"text": "..."}   for phones too small to run Llama themselves.
The phone builds the prompt and CHECKS the answer against the verified advice, exactly as for its own Llama.
Status: written but not yet run on a Pi. Test /health first.
"""
import argparse, json, os, threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

LLM = None
LOCK = threading.Lock()

def load(gguf):
    global LLM
    from llama_cpp import Llama
    LLM = Llama(model_path=gguf, n_ctx=2048, n_threads=4, verbose=False)

class H(SimpleHTTPRequestHandler):
    def log_message(self, *a): pass
    def _json(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path == "/health":
            return self._json(200, {"ok": True, "llm": LLM is not None})
        return super().do_GET()
    def do_POST(self):
        if self.path != "/ask": return self._json(404, {"error": "not found"})
        if LLM is None: return self._json(503, {"error": "no model loaded"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            req = json.loads(self.rfile.read(n))
            prompt = str(req["prompt"])[:6000]
            mx = max(16, min(int(req.get("max_tokens", 160)), 300))
        except Exception:
            return self._json(400, {"error": "bad request"})
        with LOCK:                       # one answer at a time: a Pi has limited memory
            out = LLM.create_completion(prompt=prompt, max_tokens=mx, temperature=0.2, stop=["<|eot_id|>", "<|end_of_text|>"])
        self._json(200, {"text": out["choices"][0]["text"].strip()})

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", default="./models"); ap.add_argument("--gguf", default="")
    ap.add_argument("--port", type=int, default=8000)
    a = ap.parse_args()
    if a.gguf: load(a.gguf)
    os.chdir(a.models)
    print("hub on port", a.port, "- llm loaded" if LLM else "- no llm (file server only)")
    ThreadingHTTPServer(("0.0.0.0", a.port), H).serve_forever()
