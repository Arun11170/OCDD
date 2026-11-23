"""Downloads extra training data (run in Windows PowerShell, needs internet):

    cd D:\OCDD
    pip install requests huggingface_hub tqdm
    python get_data.py plantseg      # 1.7 GB, CC BY 4.0: in-the-wild photos with true LESION outlines (11k images, 115 diseases)
    python get_data.py plantwild     # ~18k in-the-wild disease photos, 89 classes (CC BY-NC-ND 4.0: non-commercial)
    python get_data.py all

Resumes if the download stops. Nothing is deleted. Result in D:\OCDD\data\raw\<name>\.
"""
import hashlib, os, sys, zipfile
import requests

ROOT = "D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD"
RAW = ROOT + "/data/raw"


def download(url, dest, md5=None):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    have = os.path.getsize(dest) if os.path.exists(dest) else 0
    head = requests.head(url, allow_redirects=True, timeout=60)
    total = int(head.headers.get("content-length", 0))
    if total and have == total:
        print("already downloaded:", dest); return
    hdr = {"Range": "bytes=%d-" % have} if have and total else {}
    with requests.get(url, headers=hdr, stream=True, timeout=60) as r:
        r.raise_for_status()
        mode = "ab" if hdr and r.status_code == 206 else "wb"
        if mode == "wb": have = 0
        done = have
        with open(dest, mode) as f:
            for chunk in r.iter_content(1 << 20):
                f.write(chunk); done += len(chunk)
                if total: print("\r  %5.1f %%  %d / %d MB" % (100 * done / total, done >> 20, total >> 20), end="", flush=True)
    print()
    if md5:
        h = hashlib.md5()
        with open(dest, "rb") as f:
            for b in iter(lambda: f.read(1 << 22), b""): h.update(b)
        print("md5", "OK" if h.hexdigest() == md5 else "MISMATCH (download again)")


def plantseg():
    z = RAW + "/plantseg/Plantseg.zip"
    download("https://zenodo.org/records/16665770/files/Plantseg.zip?download=1", z, "23385d877bc84bab51b54630055c75b8")
    out = RAW + "/plantseg/data"
    if not os.path.isdir(out):
        print("unzipping ...")
        with zipfile.ZipFile(z) as zf: zf.extractall(out)
    print("PlantSeg ready:", out)


def plantwild():
    from huggingface_hub import snapshot_download
    p = snapshot_download(repo_id="uqtwei2/PlantWild", repo_type="dataset", local_dir=RAW + "/plantwild")
    print("PlantWild ready:", p)


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("plantseg", "all"): plantseg()
    if what in ("plantwild", "all"):
        try: plantwild()
        except Exception as e: print("PlantWild download failed:", e)
