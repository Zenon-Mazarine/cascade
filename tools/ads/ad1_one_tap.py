"""Ad 1 · "One tap" · 20 s, 1080x1920.

Hook → Cascade appears → six real style switches, each titled in that style's own type and
colors → "7 styles. One tap." → end card. Usage: python3 ad1_one_tap.py <footage dir> <out dir> [--frames]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from kit import *  # noqa
from common import end_card, BRAND_TOP, BRAND_BOT
import audio

FOOT, OUT = sys.argv[1], sys.argv[2]
FRAMES_ONLY = "--frames" in sys.argv
os.makedirs(OUT, exist_ok=True)

STYLES = [
    # key, title, subtitle, font, title color, bg top, bg bottom, glow, switch sfx
    ("water", "Water", "Ripples and droplets", "display-light", (127, 227, 242), (8, 52, 68), (2, 12, 18), None, "water_splash"),
    ("ink", "Ink", "Brushstrokes on paper", "serif", (237, 230, 216), (36, 32, 28), (12, 11, 10), (210, 69, 58), "ink_switch"),
    ("stone", "Stone", "A zen garden", "serif", (233, 226, 212), (52, 47, 40), (22, 20, 17), None, "stone_switch"),
    ("paper", "Paper", "Folded origami", "mono", (241, 234, 223), (58, 52, 46), (24, 22, 20), (232, 135, 91), "paper_switch"),
    ("neon", "Neon", "Signs on a rainy night", "display-black", (255, 160, 214), (24, 8, 30), (6, 5, 12), (255, 62, 165), "neon_zap"),
    ("cosmos", "Cosmos", "A drifting constellation", "display-thin", (232, 236, 255), (20, 16, 52), (3, 4, 14), (159, 140, 255), "cosmos_switch"),
]
PREV = {"water": "classic", "ink": "water", "stone": "ink", "paper": "stone", "neon": "paper", "cosmos": "neon"}

T_HOOK, T_MEET = 0.0, 2.6
T_STYLES = 4.4
SEG = 1.95
T_GRID = T_STYLES + SEG * len(STYLES)  # 16.1
T_END = T_GRID + 1.9  # 18.0
TOTAL = 20.5

BRAND_TOP, BRAND_BOT = (14, 90, 115), (59, 42, 107)
PHONE_CY = 1150
SWITCH_AT = 0.55  # where the transition starts inside each cut (the capture puts it 0.6 s in)


def still(key):
    p = os.path.join(FOOT, f"still-{key}.png")
    return Image.open(p).convert("RGB")


STILLS = {k: still(k) for k in ["classic", "water", "ink", "stone", "paper", "neon", "cosmos"]}


def load_clip(name, start, dur):
    p = os.path.join(FOOT, name + ".mp4")
    if os.path.exists(p):
        try:
            c = Clip(p, start, dur)
            if c.n > 5:
                return c
        except Exception as e:
            print("clip failed", name, e)
    return None


# Each style segment is cut from the real recording of its switch.
CUTS = {}
for key, *_ in STYLES:
    CUTS[key] = load_clip(f"switch-{PREV[key]}-{key}", 0.6 - SWITCH_AT, SEG + 0.3)
MEET = load_clip("idle-water", 0.0, 2.0)

ICON = Image.open(os.path.join(os.path.dirname(__file__), "..", "..", "store", "graphics", "icon-512.png")).convert("RGB")


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


BG_CACHE = {}


def bg(top, bot):
    k = (top, bot)
    if k not in BG_CACHE:
        BG_CACHE[k] = gradient(top, bot)
    return BG_CACHE[k]


def bg_blend(t0, b0, t1, b1, p):
    a = bg(t0, b0).astype(np.float32)
    b = bg(t1, b1).astype(np.float32)
    return (a + (b - a) * p).astype(np.uint8)


def screen_for(key, t_in_seg):
    c = CUTS.get(key)
    if c is not None:
        return c.at(t_in_seg * FPS)
    # Fallback: the previous style's still, then this style's.
    return STILLS[key] if t_in_seg > SWITCH_AT + 0.5 else STILLS[PREV[key]]


TITLES = {}


def title_layers(st):
    key, name, sub, fnt, col, top, bot, glow, _ = st
    if key not in TITLES:
        tube = glow if key in ("neon", "cosmos") else None
        big = text_layer(name, font(fnt, 150), col, glow=tube, glow_radius=22)
        light = sum(top) > 400
        small = text_layer(sub, font("text", 40), (40, 36, 32) if light else (230, 235, 245))
        TITLES[key] = (big, small)
    return TITLES[key]


def tap_ring(canvas, cx, cy, p, color):
    """A finger tap: a dot that presses in and a ring that expands."""
    if p <= 0 or p >= 1:
        return
    d = ImageDraw.Draw(canvas)
    r = 26 + 70 * ease_out(p)
    a = int(220 * (1 - p))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color + (a,), width=5)
    rr = 26 * (1 - 0.3 * math.sin(min(1, p * 3) * math.pi))
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=color + (int(160 * (1 - p)),))


ORB = {"classic": (881, 627), "water": (972, 457), "ink": (871, 189), "stone": (967, 457),
       "paper": (969, 450), "neon": (864, 185), "cosmos": (963, 454)}


def orb_point(scale, cx, cy, key):
    """Where the style button sits on the phone screen (measured at 1080 wide), in canvas coordinates."""
    sx = ORB[key][0] * SCREEN_W / 1080
    sy = ORB[key][1] * SCREEN_W / 1080
    w = (PHONE_W + 2 * SHADOW_PAD) * scale
    h = (PHONE_H + 2 * SHADOW_PAD) * scale
    x0 = cx - w / 2 + (SHADOW_PAD + BEZEL) * scale
    y0 = cy - h / 2 + (SHADOW_PAD + BEZEL) * scale
    return x0 + sx * scale, y0 + sy * scale


def frame(t):
    # ── Hook ──────────────────────────────────────────────────────────
    if t < T_MEET:
        canvas = Image.fromarray(bg((10, 14, 26), (2, 3, 8))).convert("RGBA")
        out = ease_in_out(seg(t, 2.15, 2.6))
        f1 = font("display", 96)
        word_reveal(canvas, ["Your", "home", "screen"], f1, W / 2, 800 - out * 120, t, 0.15, stagger=0.09)
        word_reveal(canvas, ["deserves", "a"], f1, W / 2, 920 - out * 120, t, 0.55, stagger=0.09)
        p = ease_back(seg(t, 0.95, 1.4))
        put_text(canvas, text_layer("personality.", font("display-black", 120), (127, 227, 242), glow=(80, 200, 230), glow_radius=26),
                 W / 2, 1060 - out * 120, alpha=clamp(p * 1.4), scale=0.6 + 0.4 * p)
        if out > 0:
            fade = Image.new("RGBA", (W, H), (8, 52, 68, int(255 * out)))
            canvas.alpha_composite(fade)
        return canvas

    # ── Meet Cascade ──────────────────────────────────────────────────
    if t < T_STYLES:
        lt = t - T_MEET
        canvas = Image.fromarray(bg((8, 52, 68), (2, 12, 18))).convert("RGBA")
        p = spring(lt / 1.2, damping=0.55, freq=1.3)
        cy = PHONE_CY + (1 - p) * 1300
        scr = MEET.at(lt * FPS) if MEET else STILLS["water"]
        place_center(canvas, phone(scr, 1.0), W / 2, cy)
        word_reveal(canvas, ["Meet", "Cascade."], font("display", 92), W / 2, 230, lt, 0.35, stagger=0.12)
        put_text(canvas, text_layer("Seven styles. One tap.", font("text", 40), (200, 230, 240)), W / 2, 330,
                 alpha=ease_out(seg(lt, 0.8, 1.2)), dy=(1 - ease_out(seg(lt, 0.8, 1.2))) * 20)
        return canvas

    # ── Style switches ────────────────────────────────────────────────
    if t < T_GRID:
        i = min(len(STYLES) - 1, int((t - T_STYLES) / SEG))
        lt = t - T_STYLES - i * SEG
        st = STYLES[i]
        prev = STYLES[i - 1] if i > 0 else ("water", "", "", "", None, (8, 52, 68), (2, 12, 18), None, None)
        # The background follows the screen: it turns over while the transition covers the phone.
        bp = ease_in_out(seg(lt, SWITCH_AT + 0.15, SWITCH_AT + 0.6))
        canvas = Image.fromarray(bg_blend(prev[5], prev[6], st[5], st[6], bp)).convert("RGBA")
        if st[7]:
            g = np.asarray(canvas.convert("RGB"))
            canvas = Image.fromarray(radial_glow(g, W / 2, 330, 520, st[7], 0.22 * bp)).convert("RGBA")
        # Phone with a gentle push-in through the whole run.
        run = (t - T_STYLES) / (T_GRID - T_STYLES)
        scale = 1.0 + 0.03 * run
        place_center(canvas, phone(screen_for(st[0], lt), scale), W / 2, PHONE_CY)
        # A tap on the style button just before each switch.
        ox, oy = orb_point(scale, W / 2, PHONE_CY, PREV[st[0]])
        light = sum(prev[5]) > 400
        tap_ring(canvas, ox, oy, seg(lt, SWITCH_AT - 0.35, SWITCH_AT + 0.35), (40, 40, 48) if light else (255, 255, 255))
        # Titles: the previous style's leaves, this one's lands with the switch.
        if i > 0:
            pb, ps = title_layers(prev)
            q = ease_in_out(seg(lt, SWITCH_AT, SWITCH_AT + 0.35))
            put_text(canvas, pb, W / 2, 205, alpha=1 - q, dy=-60 * q, blur=10 * q)
            put_text(canvas, ps, W / 2, 330, alpha=(1 - q), dy=-40 * q)
        big, small = title_layers(st)
        q = seg(lt, SWITCH_AT + 0.3, SWITCH_AT + 0.85)
        put_text(canvas, big, W / 2, 205, alpha=ease_out(q), dy=50 * (1 - ease_out(q)), scale=0.92 + 0.08 * ease_back(q), blur=8 * (1 - ease_out(q)))
        q2 = ease_out(seg(lt, SWITCH_AT + 0.5, SWITCH_AT + 0.95))
        put_text(canvas, small, W / 2, 330, alpha=q2, dy=24 * (1 - q2))
        # On the last style, the phone hands off to the grid.
        return canvas

    # ── 7 styles grid ─────────────────────────────────────────────────
    if t < T_END:
        lt = t - T_GRID
        canvas = Image.fromarray(bg_blend((20, 16, 52), (3, 4, 14), BRAND_TOP, BRAND_BOT, ease_in_out(seg(lt, 0, 0.6)))).convert("RGBA")
        keys = ["classic", "water", "ink", "stone", "paper", "neon", "cosmos"]
        # Seven phones fan out from the center.
        for j, k in enumerate(keys):
            off = j - 3
            p = spring(seg(lt, 0.05 + abs(off) * 0.05, 1.3 + abs(off) * 0.05), damping=0.6, freq=1.2)
            ang = -off * 7 * p
            x = W / 2 + off * 150 * p
            y = 1200 + abs(off) ** 1.6 * 22 * p
            ph = phone(STILLS[k], 0.42, angle=ang)
            place_center(canvas, ph, x, y)
        word_reveal(canvas, ["7", "styles."], font("display-black", 120), W / 2, 380, lt, 0.25, stagger=0.1)
        word_reveal(canvas, ["One", "tap."], font("display-black", 120), W / 2, 520, lt, 0.55, stagger=0.1, fill=(127, 227, 242))
        out = ease_in_out(seg(lt, 1.55, 1.9))
        if out > 0:
            canvas.alpha_composite(Image.new("RGBA", (W, H), BRAND_TOP + (int(255 * out),)))
        return canvas

    # ── End card ──────────────────────────────────────────────────────
    return end_card(t - T_END)


def render():
    if FRAMES_ONLY:
        for name, t in [("hook", 1.6), ("meet", 3.8), ("water", T_STYLES + 1.7), ("ink-mid", T_STYLES + SEG + 1.0),
                        ("neon", T_STYLES + 4 * SEG + 1.7), ("cosmos", T_STYLES + 5 * SEG + 1.7), ("grid", T_GRID + 1.3), ("end", T_END + 1.6)]:
            frame(t).convert("RGB").save(os.path.join(OUT, f"ad1-{name}.jpg"), quality=90)
        return
    bounds = [0, T_MEET, T_STYLES] + [T_STYLES + SEG * k for k in range(1, len(STYLES) + 1)] + [T_END, TOTAL]
    parts = []
    for k in range(len(bounds) - 1):
        a, b = bounds[k], bounds[k + 1]
        path = os.path.join(OUT, f"ad1-part{k:02d}.mp4")
        sc = Scene(path)
        for fi in range(int(round(a * FPS)), int(round(b * FPS))):
            sc.add(frame(fi / FPS))
        sc.close()
        parts.append(path)
        print("scene", k, "done", flush=True)
    concat(parts, os.path.join(OUT, "ad1-video.mp4"))

    # Soundtrack: the bed, a tap + each style's own switch sound on every cut, a riser into the grid.
    tr = audio.Track(TOTAL)
    tr.sfx("water_bubble", 0.95, 0.35, rate=0.8)
    tr.sfx("water_soft", T_MEET + 0.1, 0.5)
    for i, st in enumerate(STYLES):
        at = T_STYLES + i * SEG + SWITCH_AT
        tr.sfx("classic_tick", at - 0.05, 0.5)
        tr.sfx(st[8], at, 0.75)
        tr.sidechain(at, 0.5, 0.35)
    tr.sfx("cosmos_sparkle", T_GRID + 0.3, 0.6)
    tr.sfx("water_drop", T_END + 0.05, 0.6)
    tr.sfx("cosmos_chime", T_END + 0.9, 0.45)
    bed = audio.music_bed(TOTAL, 100, [["A3", "C4", "E4", "G4"], ["F3", "A3", "C4", "E4"], ["C4", "E4", "G4", "B4"], ["G3", "B3", "D4", "E4"]],
                          start_drums=T_STYLES, end_drums=T_END - 0.3, riser_at=T_GRID, impact_at=T_END)
    wav = os.path.join(OUT, "ad1-audio.wav")
    tr.save(wav, bed, music_gain=0.85)
    mux(os.path.join(OUT, "ad1-video.mp4"), wav, os.path.join(OUT, "Cascade-ad-one-tap.mp4"))
    print("done")


if __name__ == "__main__":
    render()
