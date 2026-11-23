"""Small helpers shared by the training scripts: pause request, low priority, safe checkpoint saving.

Pause:   create the file D:\\OCDD\\PAUSE (double-click pause.bat). The running step finishes its current epoch, saves, and exits with code 3.
Resume:  double-click run_all.bat again. Finished steps are skipped and the unfinished step continues from its last saved epoch.
Closing the window or pressing Ctrl+C also works: you only lose the epoch that was running (about 5-10 minutes of work).
"""
import os, sys

ROOT = os.environ.get("OCDD_ROOT") or ("D:/OCDD" if os.name == "nt" else "/mnt/d/OCDD")
PAUSE = os.path.join(ROOT, "PAUSE")
PAUSED_CODE = 3


def pause_requested():
    return os.path.exists(PAUSE)


def exit_paused(what):
    print("\n=== PAUSED after %s (progress saved). Run run_all.bat to continue. ===" % what, flush=True)
    sys.exit(PAUSED_CODE)


def lower_priority():
    """Run below normal priority so the laptop stays responsive for studying; worker processes inherit this."""
    try:
        if os.name == "nt":
            import ctypes
            ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), 0x00004000)   # BELOW_NORMAL
        else:
            os.nice(5)
    except Exception:
        pass


def atomic_save(obj, path):
    """Write to a temporary file first, so a power cut or Ctrl+C can never leave a half-written checkpoint."""
    import torch
    tmp = path + ".tmp"
    torch.save(obj, tmp)
    os.replace(tmp, path)
