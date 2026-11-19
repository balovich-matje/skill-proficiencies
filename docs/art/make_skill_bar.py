"""Generates the skill XP bar's fill art. Original pixels, procedural, seeded.

    python3 docs/art/make_skill_bar.py [out_dir]

Writes three 182x5 PNGs (default: the GUI sprite folder under src/main/resources):

  skill_bar_progress.png  opaque GREYSCALE body. Drawn with the skill colour as the
                          vertex tint, so the GPU multiplies grey x colour per channel.
                          Grey, not green: a tint multiplied over a green source turns
                          every non-green skill colour muddy (tint x green), which is the
                          trap a recoloured vanilla progress sprite falls into.
  skill_bar_sheen.png     WHITE pixels with partial alpha, drawn untinted over the body.
                          Multiplication can only darken, so the body can never get
                          lighter than the skill colour itself; the sheen alpha-blends
                          toward white instead, which is what gives saturated and dark
                          colours a real highlight.
  skill_bar_glow.png      WHITE mask over the fill's interior (no border, no notches),
                          drawn untinted at a per-frame alpha by the HUD animations
                          (edge glint, segment tick, almost-there pulse). Same reason as
                          the sheen: brightening has to blend toward white.

Nothing here is read from or traced off Mojang's textures. Only the GEOMETRY follows the
vanilla experience bar, because the fill has to sit on top of vanilla's background:
182x5, transparent top/bottom corner pixels, and segment separators in the same columns
as the background's notches: every 10px, and a TWO-column notch at 90-91 in the centre
(182 is even, so the middle notch is doubled). That makes 18 cells of 9px each. Measured
off the 26.3, 26.2, 1.21.11, 1.21.1 sprites and 1.20.1's icons.png — identical on all.
"""
import os
import random
import sys

from PIL import Image

W, H = 182, 5
# Notch columns of the vanilla background (geometry only): 10..90, 91, 101..171.
# 90 AND 91: the centre notch is two columns wide. Missing 90/91 merged the two middle
# cells into one 20px cell (the 1.7.0-dev bug). SkillXpHudBar.SEPARATORS mirrors this.
SEPARATORS = set(range(10, 91, 10)) | {91} | set(range(101, 172, 10))

# Grey levels, 0..1 of the skill colour. Row 2 is the bright core at exactly the skill
# colour; row 1 a touch darker, row 3 the shadow side. Kept shallow on purpose: darkening
# yellow/orange by much turns it olive/brown, which reads as "dirty" rather than "shaded".
ROW_LEVEL = {1: 0.86, 2: 1.00, 3: 0.72}
BORDER = 0.30
SEPARATOR = 0.32
EDGE_DARKEN = 0.07   # the column right before a notch: the cell rounds off into it
NOISE = 0.06         # mottling, so the fill reads as painted rather than a flat rect

# Glow mask alpha per interior row: brightest on the core, a little on top, least on the
# shadow side, so a brightened cell still reads as a rounded tube.
ROW_GLOW = {1: 0.65, 2: 1.00, 3: 0.45}

SEED = 0x5B1A2


def segments():
    """Interior x-ranges between separators/borders."""
    cuts = [0] + sorted(SEPARATORS) + [W - 1]
    return [(a + 1, b - 1) for a, b in zip(cuts, cuts[1:]) if b - a > 1]


def build():
    rnd = random.Random(SEED)
    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sheen = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    glow = Image.new("RGBA", (W, H), (255, 255, 255, 0))

    def put(x, y, g):
        v = max(0, min(255, round(g * 255)))
        body.putpixel((x, y), (v, v, v, 255))

    for x in range(W):
        for y in range(H):
            edge_row = y in (0, H - 1)
            if edge_row and x in (0, W - 1):
                continue  # rounded ends, transparent like the background's corners
            if edge_row or x in (0, W - 1):
                put(x, y, BORDER + rnd.uniform(-0.05, 0.05))
            elif x in SEPARATORS:
                put(x, y, SEPARATOR + rnd.uniform(-0.03, 0.03))
            else:
                g = ROW_LEVEL[y] + rnd.uniform(-NOISE, NOISE)
                if (x + 1) in SEPARATORS or x == W - 2:
                    g -= EDGE_DARKEN
                put(x, y, min(1.0, g))
                glow.putpixel((x, y), (255, 255, 255, round(ROW_GLOW[y] * 255)))

    for a, b in segments():
        # A glint near each cell's left end, like light caught on a rounded tube, and a
        # faint streak along the core row for the rest of the cell. Position jitters a
        # pixel per cell so it doesn't read as a stamped pattern.
        g0 = a + 1 + rnd.randint(0, 1)
        for x in range(a, b + 1):
            core = 0.10 + rnd.uniform(0.0, 0.05)
            top = 0.0
            if g0 <= x <= g0 + 2:
                core = 0.42 if x == g0 + 1 else 0.30
                top = 0.16
            elif x == g0 - 1 or x == g0 + 3:
                core = 0.20
            sheen.putpixel((x, 2), (255, 255, 255, round(core * 255)))
            if top:
                sheen.putpixel((x, 1), (255, 255, 255, round(top * 255)))
    return body, sheen, glow


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        here, "..", "..", "src", "main", "resources", "assets", "specialities",
        "textures", "gui", "sprites", "hud")
    os.makedirs(out, exist_ok=True)
    body, sheen, glow = build()
    body.save(os.path.join(out, "skill_bar_progress.png"), optimize=True)
    sheen.save(os.path.join(out, "skill_bar_sheen.png"), optimize=True)
    glow.save(os.path.join(out, "skill_bar_glow.png"), optimize=True)
    print("wrote", os.path.normpath(out))


if __name__ == "__main__":
    main()
