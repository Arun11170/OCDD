"""Live progress for long training runs: a status line every ~20 s (percent, speed, time left) plus automatic worker count."""
import os, time

def free_gb():
    try:
        if os.name == "nt":
            import ctypes
            class M(ctypes.Structure):
                _fields_ = [("l", ctypes.c_ulong), ("load", ctypes.c_ulong), ("tp", ctypes.c_ulonglong), ("ap", ctypes.c_ulonglong),
                            ("tpf", ctypes.c_ulonglong), ("apf", ctypes.c_ulonglong), ("tv", ctypes.c_ulonglong), ("av", ctypes.c_ulonglong), ("x", ctypes.c_ulonglong)]
            m = M(); m.l = ctypes.sizeof(M); ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))
            return m.ap / 2 ** 30
        import psutil
        return psutil.virtual_memory().available / 2 ** 30
    except Exception:
        return 8.0

def auto_workers(cap=10, per_worker_gb=0.7, keep_gb=2.5, reserve=0.10):
    """Uses up to 90 % of the CPU threads (10 % stays free for you) minus 1 for the main process,
    and never more workers than free RAM allows (each worker needs about 0.7 GB, because every worker imports torch)."""
    n = os.cpu_count() or 4
    by_cpu = max(1, int(n * (1 - reserve)) - 1)
    by_ram = int((free_gb() - keep_gb) / per_worker_gb)
    w = max(2, min(by_cpu, cap, by_ram))
    limit = "RAM" if w == by_ram and by_ram < min(by_cpu, cap) else ("CPU reserve" if w == by_cpu else "cap")
    print("workers: %d  (cpu threads %d, free RAM %.1f GB, limited by %s; %d%% of CPU kept free for you)" % (w, n, free_gb(), limit, int(reserve * 100)), flush=True)
    return w

def fmt(sec):
    sec = int(max(sec, 0)); h, r = divmod(sec, 3600); m, s = divmod(r, 60)
    return "%dh%02dm" % (h, m) if h else "%dm%02ds" % (m, s)

class Beat:
    def __init__(self, label, epochs=1, every=20, start_ep=0):
        self.label, self.epochs, self.every, self.start_ep = label, epochs, every, start_ep
        self.t0 = time.time(); self.last = 0; self.items0 = None
    def tick(self, ep, i, n, loss=None):
        now = time.time()
        if now - self.last < self.every: return
        self.last = now
        frac = (ep + (i + 1) / n) / self.epochs
        el = now - self.t0
        done = frac - self.start_ep / self.epochs                # progress made in THIS session (after a resume)
        eta = el / max(done, 1e-6) * (1 - frac)
        gpu = ""
        try:
            import torch
            if torch.cuda.is_available(): gpu = "  GPU mem %.1f GB" % (torch.cuda.max_memory_allocated() / 2 ** 30)
        except Exception: pass
        print("  [%s] epoch %d/%d  %3d%%  batch %d/%d%s  elapsed %s  about %s left%s  (%s)" % (
            self.label, ep + 1, self.epochs, int(100 * frac), i + 1, n, ("  loss %.3f" % loss) if loss is not None else "",
            fmt(el), fmt(eta), gpu, time.strftime("%H:%M:%S")), flush=True)
