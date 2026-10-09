r"""30-second GPU speed test (no photos, no data loading): which model can this laptop train in a sensible time?
   cd D:\OCDD ; python speedtest.py
Prints images/second for each model and how long one epoch of 64,000 photos and a whole 20-epoch run would take."""
import time, torch, torch.nn as nn
from torchvision import models
dev = "cuda"
torch.backends.cudnn.benchmark = True
specs = [("mobilenet_v3_large", lambda: models.mobilenet_v3_large(weights=None, num_classes=95), 64),
         ("efficientnet_b0",    lambda: models.efficientnet_b0(weights=None, num_classes=95), 64),
         ("efficientnet_v2_s",  lambda: models.efficientnet_v2_s(weights=None, num_classes=95), 32)]
print(torch.cuda.get_device_name(0))
for name, mk, bs in specs:
    for cl in (True, False):
        m = mk().to(dev)
        if cl: m = m.to(memory_format=torch.channels_last)
        opt = torch.optim.AdamW(m.parameters(), 1e-3); sc = torch.amp.GradScaler()
        x = torch.randn(bs, 3, 224, 224, device=dev); y = torch.randint(0, 95, (bs,), device=dev)
        if cl: x = x.contiguous(memory_format=torch.channels_last)
        def step():
            with torch.autocast("cuda"):
                l = nn.functional.cross_entropy(m(x), y)
            opt.zero_grad(set_to_none=True); sc.scale(l).backward(); sc.step(opt); sc.update()
        for _ in range(5): step()
        torch.cuda.synchronize(); t = time.time(); n = 25
        for _ in range(n): step()
        torch.cuda.synchronize(); ips = n * bs / (time.time() - t)
        print("%-20s batch %2d  channels_last=%-5s  %6.0f images/s   one epoch (64k photos) %5.1f min   20 epochs %5.1f h" % (name, bs, cl, ips, 64000 / ips / 60, 20 * 64000 / ips / 3600), flush=True)
        del m, opt, x; torch.cuda.empty_cache()
