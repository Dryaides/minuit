"""Design-time contrast check of every shipped chroma x variant, with the floors the runtime used to enforce
through Ink.readable / Shapes.textOn (4.5:1 for text, 3:1 for a mark). Official palettes are no longer
shifted at runtime (RULES.md, 2026-11-11), so a failure here is fixed in the chroma file.

    python tools/palette_contrast.py      # lists failures, exits 1 when there are any

The glass variant is derived in GlassPalette.java and family overrides are not applied: both are out of
scope here.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHROMAS = ROOT / "core/src/main/resources/assets/minuit/minuit/chromas"
TEXT, MARK = 4.5, 3.0
DARK_FACE = 0.06
DARK_FIELD = 0xFF16161C

# (ink role, face, floor, where the component draws it). A face is a palette role, or one of the faces the
# code derives: fieldFace (FieldInk.face), darkField (FormLook.fieldBack, dark for every family's light field
# text). Marks that land on the surface are covered by the 4.5:1 text rule of the same ink.
# Variants whose values the owner set by eye in the palette review (2026-11-12), the c51bda32 values kept over
# the contrast fixes: the check does not hold them.
BY_EYE = {"vanilla/light", "vanilla/dark", "bedrock/light", "bedrock/dark", "wiki/dark", "teal/dark"}

RULES = [
    ("text", "surface", TEXT, "body text"),
    ("text", "section", TEXT, "body text on a card"),
    ("text", "well", TEXT, "text in a list"),
    ("textSoft", "surface", TEXT, "Ink.soft"),
    ("raisedText", "raised", TEXT, "button label"),
    ("raisedText", "raisedHover", TEXT, "button label, hovered"),
    ("primaryText", "primary", TEXT, "primary label"),
    ("primaryText", "primaryHover", TEXT, "primary label, hovered"),
    ("destructiveText", "destructive", TEXT, "destructive label"),
    ("destructiveText", "destructiveHover", TEXT, "destructive label, hovered"),
    ("mark", "surface", TEXT, "BadgeComponent PRIMARY; Heatmap/Steps mark"),
    ("good", "surface", TEXT, "BadgeComponent GOOD; Sparkline up"),
    ("warn", "surface", TEXT, "BadgeComponent WARN"),
    ("bad", "surface", TEXT, "Badge/Balance/Cart/Checkout/ProductCard bad text; Sparkline down, Kanban alert, Steps error"),
    ("fieldAlert", "darkField", TEXT, "TagInput alert in a field"),
    ("accent", "surface", TEXT, "ToastArt title; Calendar/Spinner mark"),
    ("twinMark", "fieldFace", TEXT, "KeybindField listening, the dark variant's mark"),
    ("link", "surface", TEXT, "RichText link"),
    ("disabledText", "disabled", MARK, "Ink.disabled on a disabled button"),
]


def argb(h):
    v = int(h.lstrip("#"), 16)
    return v | 0xFF000000 if len(h.lstrip("#")) == 6 else v


def over(back, face):
    a = (face >> 24 & 0xFF) / 255
    out = 0xFF000000
    for s in (16, 8, 0):
        out |= round((back >> s & 0xFF) * (1 - a) + (face >> s & 0xFF) * a) << s
    return out


def lum(c):
    ch = [(c >> s & 0xFF) / 255 for s in (16, 8, 0)]
    ch = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in ch]
    return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2]


def mix(a, b, t):
    out = 0
    for sh in (24, 16, 8, 0):
        x, y = a >> sh & 0xFF, b >> sh & 0xFF
        out |= int(x + (y - x) * t) << sh
    return out


def field_face(p):
    """FieldInk.face: the well when dark and opaque, else the darker of text and surface deepened."""
    well = p["well"]
    if well >> 24 == 0xFF and lum(well) <= DARK_FACE:
        return well
    face = (p["text"] if lum(p["text"]) < lum(p["surface"]) else p["surface"]) | 0xFF000000
    while lum(face) > DARK_FACE:
        face = mix(face, 0xFF000000, 0.25)
    return face


def field_alert(bad, dark_bad):
    """TagInputComponent.alertInk: the light red moved halfway or more toward the dark variant's red."""
    if bad == dark_bad:
        return bad
    for step in range(5, 10):
        red = mix(bad | 0xFF000000, dark_bad | 0xFF000000, step / 10)
        if ratio(red, DARK_FIELD) >= TEXT:
            return red
    return dark_bad


def ratio(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def main():
    failures = 0
    for path in sorted(CHROMAS.glob("*.json")):
        variants = json.loads(path.read_text(encoding="utf-8")).get("variants", {})
        for name, roles in variants.items():
            if not isinstance(roles, dict) or f"{path.stem}/{name}" in BY_EYE:
                continue
            p = {k: argb(v) for k, v in roles.items() if isinstance(v, str) and v.startswith("#")}
            if "link" not in p and "accent" in p:
                p["link"] = p["accent"]
            if "mark" not in p and "accent" in p:
                p["mark"] = p["accent"]
            twin = {k: argb(v) for k, v in variants.get("dark", {}).items() if isinstance(v, str) and v.startswith("#")}
            twin.setdefault("mark", twin.get("accent", p.get("accent")))
            p["twinMark"] = twin["mark"] if twin.get("mark") else p["mark"]
            p["fieldAlert"] = field_alert(p["bad"], twin.get("bad", p["bad"]))
            surface = over(0xFFFFFFFF, p["surface"])
            p["fieldFace"] = field_face(p)
            p["darkField"] = DARK_FIELD
            for ink, face, floor, where in RULES:
                if ink not in p or face not in p:
                    continue
                back = surface if face == "surface" else over(surface, p[face])
                r = ratio(over(back, p[ink]), back)
                if r < floor:
                    failures += 1
                    print(f"{path.stem}/{name}: {ink} on {face} {r:.2f} < {floor} ({where})")
    print(f"{failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
