"""Contrast of the code token colours on the code surface, over every family x chroma x variant theme.
Reads site/data/themes.json and the --t-* mixes in site/skins/minuit/skin.css; exits 1 under 4.5:1."""
import json, pathlib, re, sys

SITE = pathlib.Path(__file__).resolve().parent.parent / "site"
MIN = 4.5


def rgb(h):
    h = h[-6:]
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def lum(c):
    c = [x / 255 for x in c]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ratio(a, b):
    hi, lo = sorted([lum(a), lum(b)], reverse=True)
    return (hi + 0.05) / (lo + 0.05)


css = (SITE / "skins/minuit/skin.css").read_text(encoding="utf-8")
tokens = {}
for name, expr in re.findall(r"--(t-[a-z]+): ([^;]+);", css):
    m = re.match(r"color-mix\(in srgb, var\(--r-(\w+)\) (\d+)%, var\(--r-(\w+)\)\)", expr)
    tokens[name] = (m.group(1), int(m.group(2)), m.group(3)) if m else (re.search(r"--r-(\w+)", expr).group(1), 100, None)

worst = {}
for f in json.loads((SITE / "data/themes.json").read_text(encoding="utf-8"))["families"]:
    for c in f["chromas"]:
        for v in ("light", "dark"):
            p = c[v]
            for t, (a, pct, b) in tokens.items():
                col = rgb(p[a]) if b is None else [round(x * pct / 100 + y * (100 - pct) / 100) for x, y in zip(rgb(p[a]), rgb(p[b]))]
                r = ratio(col, rgb(p["surface"]))
                if t not in worst or r < worst[t][0]:
                    worst[t] = (r, f["id"], c["id"], v)
bad = False
for t, (r, f, c, v) in sorted(worst.items()):
    print(f"{t:10} min {r:5.2f}  ({f} {c} {v})")
    bad |= r < MIN
sys.exit(1 if bad else 0)
