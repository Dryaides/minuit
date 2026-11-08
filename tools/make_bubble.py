"""Regenerates the subtitle bubble atlas: python tools/make_bubble.py

The bubble of the Polly logo (polly_bubble_emote_dots_red_jungle), sprite EMOTE of
_mod-pages/_work/logos/blender/polly_sprites.py, pixel for pixel and in its palette, cut for a
9-slice the way wide_bubble() stretches it: column 6 repeats across, rows 4 and 7 repeat down.
The bottom edge is the plain one of wide_bubble(tail="center"); the tail is a separate piece.

Atlas (64 x 64, textures/gui/bubble.png), read by Bubble:
  body at 0,0, 47 x 41: columns 0-5 left cap, 6-37 the repeated column (32 copies), 38-46 right cap;
       rows 0-2 top, 3-18 the repeated white row (16 copies), 19-20, 21-36 the repeated blue row
       (16 copies), 37-40 bottom and edge.
  tail at 48,0, 4 x 4: its first row is drawn over the bubble's bottom edge, column 1 over the speaker.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'core', 'src', 'main', 'resources', 'assets', 'minuit', 'textures', 'gui',
                   'bubble.png')

PAL = {".": None, "N": (30, 36, 52), "W": (250, 250, 246), "c": (232, 240, 248), "C": (172, 192, 214)}

# EMOTE rows 1 to 11, the last one the plain bottom edge of the centre-tail bubble.
BODY = [
    "..NNNNNNNNNNNN..",
    ".NWWWWWWWWWWWWN.",
    "NWWWWWWWWWWWWWWN",
    "NWWWWWWWWWWWWWWN",
    "NWWWWWWWWWWWWWWN",
    "NcccccccccccccWN",
    "NcccccccccccccCN",
    "NcccccccccccccCN",
    "NCcccccccccccCCN",
    ".NCCCCCCCCCCCCN.",
    "..NNNNNNNNNNNN..",
]
# wide_bubble(tail="center") cells, columns dx -1 to 2 of the tail.
TAIL = [
    "..CC",
    ".NCN",
    "NCN.",
    "NN..",
]
MID, MID_COPIES = 6, 32
ROW_A, ROW_B, COPIES = 3, 6, 16


def main():
    rows = BODY[:ROW_A] + [BODY[ROW_A]] * COPIES + BODY[ROW_A + 1:ROW_B] + [BODY[ROW_B]] * COPIES + BODY[ROW_B + 1:]
    rows = [r[:MID] + r[MID] * MID_COPIES + r[MID + 1:] for r in rows]
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if PAL[c]:
                img.putpixel((x, y), PAL[c] + (255,))
    for y, r in enumerate(TAIL):
        for x, c in enumerate(r):
            if PAL[c]:
                img.putpixel((48 + x, y), PAL[c] + (255,))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(OUT, len(rows[0]), "x", len(rows))


if __name__ == "__main__":
    main()
