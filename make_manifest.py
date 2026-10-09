"""Builds manifest.json for the model server the app downloads from.

  python make_manifest.py models\llama --full Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte
  python make_manifest.py models\llama --full <full.pte> --lite <light.pte>

The folder must contain tokenizer.model and the .pte file(s). To test the Light download path before the real light build
exists, point --lite at the same file or at a copy with another name.
Then serve the folder on your PC:   python -m http.server 8000 --directory models\llama
and type  <PC-wifi-IP>:8000  into the app (find the IP with `ipconfig`; allow Python through the Windows firewall).
"""
import argparse, hashlib, json, os, sys

def sha(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()

ap = argparse.ArgumentParser()
ap.add_argument('folder')
ap.add_argument('--full'); ap.add_argument('--lite')
a = ap.parse_args()
m = {}
for key, name in (('full', a.full), ('lite', a.lite), ('tokenizer', 'tokenizer.model')):
    if not name: continue
    p = os.path.join(a.folder, name)
    if not os.path.isfile(p): sys.exit('missing file: ' + p)
    print('hashing', name, '...')
    m[key] = {'file': name, 'sha256': sha(p), 'size': os.path.getsize(p)}
json.dump(m, open(os.path.join(a.folder, 'manifest.json'), 'w'), indent=1)
print('wrote', os.path.join(a.folder, 'manifest.json'))
