"""Turns the phone's metrics.csv into the tables for the technical report.

  1. After 20+ scans on the phone, copy  Android/data/com.cropadvisor/files/metrics.csv  to the PC.
  2. python tools\\summarize_metrics.py metrics.csv --capacity-mah 5000 --voltage 3.85 --phone "Model, chipset, RAM"

Battery energy is an ESTIMATE from the battery-percentage drop (percent is coarse, so run >= 30 advisories in one go on battery,
not charging). For a precise power figure also use a USB power meter and quote that number next to this one.
"""
import argparse, csv, statistics as st, sys


def pct(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))] if v else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--capacity-mah", type=float, default=0)
    ap.add_argument("--voltage", type=float, default=3.85)
    ap.add_argument("--phone", default="(fill in: model, chipset, RAM, Android version)")
    a = ap.parse_args()
    rows = list(csv.DictReader(open(a.csv, encoding="utf-8")))
    if not rows: sys.exit("empty file")
    g = lambda step: [r for r in rows if r["step"] == step]
    f = lambda rs, k: [float(r[k]) for r in rs]

    print("## Device\n%s\n" % a.phone)
    print("## Latency and memory (measured on the phone, n = number of runs)\n")
    print("| Step | n | median | p95 | notes |\n|---|---|---|---|---|")
    v = g("vision")
    if v:
        ms = f(v, "ms")
        print("| Photo model (vision) | %d | %.0f ms | %.0f ms | app memory (PSS) up to %.0f MB |" % (len(ms), st.median(ms), pct(ms, 95), max(f(v, "mem_mb"))))
    ll = g("llama_load")
    if ll:
        ms = f(ll, "ms")
        print("| Llama load (once) | %d | %.1f s | %.1f s | model stays loaded afterwards |" % (len(ms), st.median(ms) / 1000, pct(ms, 95) / 1000))
    for lang in sorted({r["lang"] for r in g("llama_advisory")}):
        ad = [r for r in g("llama_advisory") if r["lang"] == lang]
        ms = f(ad, "ms"); tps = f(ad, "tokens_per_s"); mem = f(ad, "mem_mb")
        print("| Llama advisory (%s) | %d | %.1f s | %.1f s | %.1f tokens/s median; Llama process memory up to %.0f MB |" % (lang, len(ms), st.median(ms) / 1000, pct(ms, 95) / 1000, st.median(tps), max(mem)))

    cp, cf = len(g("check_pass")), len(g("check_fail"))
    if cp + cf:
        print("\nSafety check on Llama advisories: %d of %d passed (%.0f%%); the rest were replaced by the verified text.\n" % (cp, cp + cf, 100 * cp / (cp + cf)))
        print("| Language | passed | runs | pass rate | enable Llama for it? |\n|---|---|---|---|---|")
        for lang in sorted({r["lang"] for r in g("check_pass") + g("check_fail")}):
            p_ = sum(1 for r in g("check_pass") if r["lang"] == lang); t_ = p_ + sum(1 for r in g("check_fail") if r["lang"] == lang)
            print("| %s | %d | %d | %.0f%% | %s |" % (lang, p_, t_, 100 * p_ / t_, "yes" if t_ >= 3 and p_ / t_ >= 0.8 else "no (show verified text)"))
        print()

    bat = [r for r in rows if int(r["battery_pct"]) >= 0]
    if len(bat) > 1:
        t0, t1 = int(bat[0]["time_ms"]), int(bat[-1]["time_ms"])
        b0, b1 = int(bat[0]["battery_pct"]), int(bat[-1]["battery_pct"])
        mins = (t1 - t0) / 60000
        chg = any(r["charging"] == "true" or r["charging"] == "True" for r in bat)
        print("\n## Battery and temperature\n")
        print("- Session: %.1f min, battery %d%% -> %d%% (%d points)%s" % (mins, b0, b1, b0 - b1, "  **WARNING: phone was charging at times, drain figures are invalid**" if chg else ""))
        print("- Battery temperature: start %.1f C, max %.1f C" % (float(bat[0]["battery_temp_c"]), max(f(bat, "battery_temp_c"))))
        n_adv = len(g("llama_advisory")); n_scan = len(v)
        if a.capacity_mah and b0 > b1 and not chg:
            wh_total = a.capacity_mah / 1000 * a.voltage * (b0 - b1) / 100
            print("- Estimated energy used in the session: %.2f Wh (capacity %.0f mAh at %.2f V)" % (wh_total, a.capacity_mah, a.voltage))
            if n_adv: print("- Roughly %.2f Wh (%.0f J) per scan-plus-advisory (session energy / %d advisories; includes screen and idle time)" % (wh_total / n_adv, wh_total * 3600 / n_adv, n_adv))
        else:
            print("- (give --capacity-mah and run on battery to get an energy estimate)")


if __name__ == "__main__":
    main()
