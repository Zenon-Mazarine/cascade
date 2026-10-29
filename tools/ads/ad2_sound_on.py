"""Ad 2 · "Sound on" · 13 s, 1080x1920.

Cascade's styles sound different. Ink's thumb-index tabs play a Japanese scale as you slide,
Water bubbles when you pull it up. Usage: python3 ad2_sound_on.py <footage dir> <out dir> [--frames]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from kit import *  # noqa
from common import end_card
import audio

FOOT, OUT = sys.argv[1], sys.argv[2]
FRAMES_ONLY = "--frames" in sys.argv
os.makedirs(OUT, exist_ok=True)

T_INK, T_WATER, T_END, TOTAL = 1.6, 6.4, 10.4, 13.0
# In the recordings the swipe starts 0.6 s in. Ink: 2.6 s down the tabs. Water: pull at 2.1 s.
INK_IN, WATER_IN = 0.2, 1.0
INK_SWIPE = (0.6, 3.2)
TABS = 8
PULL = (2.1, 2.8)


def clip(name, start, dur, fallback):
    p = os.path.join(FOOT, name + ".mp4")
    if os.path.exists(p):
        try:
            c = Clip(p, start, dur)
            if c.n > 5:
                return c
        except Exception as e:
            print("clip failed", name, e)
    print("using still for", name)
    return fallback


INK = clip("scrub-ink", INK_IN, T_WATER - T_INK + 0.3, Image.open(os.path.join(FOOT, "still-ink.png")).convert("RGB"))
WATER = clip("pull-water", WATER_IN, T_END - T_WATER + 0.3, Image.open(os.path.join(FOOT, "still-water.png")).convert("RGB"))


def frame_of(c, lt):
    return c.at(lt * FPS) if isinstance(c, Clip) else c


INK_BG = gradient((246, 241, 230), (222, 211, 190))
WATER_BG = gradient((8, 52, 68), (2, 12, 18))
DARK_BG = gradient((10, 14, 26), (2, 3, 8))
SEAL = (184, 50, 42)


def speaker(canvas, cx, cy, t, color=(255, 255, 255)):
    d = ImageDraw.Draw(canvas)
    s = 1.0
    d.polygon([(cx - 70, cy - 30), (cx - 30, cy - 30), (cx + 20, cy - 75), (cx + 20, cy + 75), (cx - 30, cy + 30), (cx - 70, cy + 30)], fill=color)
    for k in range(3):
        p = (t * 1.6 - k * 0.25) % 1.0
        r = 50 + k * 34
        a = int(255 * (1 - p) * clamp(t * 3))
        d.arc([cx + 20 - r, cy - r, cx + 20 + r, cy + r], -45, 45, fill=color + (a,), width=10)


def note_burst(canvas, x, y, age, color, label):
    """A pluck: a seal-red ring expanding from the tab, its note name floating left."""
    if age < 0 or age > 1.2:
        return
    d = ImageDraw.Draw(canvas)
    p = age / 1.2
    r = 20 + 120 * ease_out(p)
    d.ellipse([x - r, y - r, x + r, y + r], outline=color + (int(200 * (1 - p)),), width=6)
    lay = text_layer(label, ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 64), color)
    put_text(canvas, lay, x + 70 + 50 * ease_out(p), y - 110 * ease_out(p), alpha=clamp((1 - p) * 1.6))


def frame(t):
    if t < T_INK:
        canvas = Image.fromarray(DARK_BG).convert("RGBA")
        q = ease_back(seg(t, 0.1, 0.6))
        speaker(canvas, W / 2 - 20, 760, t)
        word_reveal(canvas, ["Sound", "on."], font("display-black", 130), W / 2, 1020, t, 0.3, stagger=0.12)
        put_text(canvas, text_layer("Your home screen has never sounded like this.", font("text", 40), (190, 200, 215)), W / 2, 1140,
                 alpha=ease_out(seg(t, 0.7, 1.1)))
        out = ease_in_out(seg(t, 1.3, T_INK))
        if out:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (246, 241, 230, int(255 * out))))
        return canvas

    if t < T_WATER:
        lt = t - T_INK
        canvas = Image.fromarray(INK_BG).convert("RGBA")
        p = ease_out(seg(lt, 0, 0.5))
        cx = W / 2 - 110
        ph = phone(frame_of(INK, lt), 1.0)
        place_center(canvas, ph, cx, 1180 + (1 - p) * 300)
        word_reveal(canvas, ["Slide", "the", "tabs."], font("serif", 96), W / 2, 210, lt, 0.1, stagger=0.1, fill=(26, 23, 20))
        put_text(canvas, text_layer("Ink plays a Japanese scale.", font("serif-italic", 46), (110, 100, 90)), W / 2, 320,
                 alpha=ease_out(seg(lt, 0.5, 0.9)))
        # Notes: one per tab the thumb crosses.
        scr_x0 = cx - SCREEN_W / 2
        a, b = INK_SWIPE[0] + INK_IN - 0.0, INK_SWIPE[1] + INK_IN
        names = ["♪", "♫", "♪", "♩", "♫", "♪", "♩", "♫"]
        top = 1180 - SCREEN_H / 2
        for k in range(TABS):
            hit = (a - INK_IN) + (k + 0.5) / TABS * (b - a)
            y = top + SCREEN_H * (0.30 + (k + 0.5) / TABS * 0.45)
            note_burst(canvas, scr_x0 + SCREEN_W - 10, y, lt - hit, SEAL, names[k])
        out = ease_in_out(seg(lt, T_WATER - T_INK - 0.35, T_WATER - T_INK))
        if out:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (8, 52, 68, int(255 * out))))
        return canvas

    if t < T_END:
        lt = t - T_WATER
        canvas = Image.fromarray(WATER_BG).convert("RGBA")
        place_center(canvas, phone(frame_of(WATER, lt), 1.0), W / 2, 1180)
        word_reveal(canvas, ["Pull", "the", "water", "up."], font("display-light", 92), W / 2, 210, lt, 0.1, stagger=0.09, fill=(127, 227, 242))
        put_text(canvas, text_layer("Water bubbles under your thumb.", font("text", 42), (200, 230, 240)), W / 2, 320,
                 alpha=ease_out(seg(lt, 0.5, 0.9)))
        # Bubbles rising around the phone while the water is pulled.
        rng = np.random.default_rng(4)
        d = ImageDraw.Draw(canvas)
        for k in range(26):
            st = PULL[0] - WATER_IN + rng.uniform(-0.3, 1.2)
            age = lt - st
            if 0 < age < 1.8:
                x = W / 2 + rng.uniform(-470, 470)
                y = 1850 - age * rng.uniform(380, 700)
                r = rng.uniform(8, 26)
                d.ellipse([x - r, y - r, x + r, y + r], outline=(160, 235, 250, int(200 * (1 - age / 1.8))), width=3)
        out = ease_in_out(seg(lt, T_END - T_WATER - 0.3, T_END - T_WATER))
        if out:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (14, 90, 115, int(255 * out))))
        return canvas

    return end_card(t - T_END, "Seven styles. Seven sounds.")


def render():
    if FRAMES_ONLY:
        for name, t in [("speaker", 1.0), ("ink", T_INK + 2.0), ("water", T_WATER + 2.4), ("end", T_END + 1.6)]:
            frame(t).convert("RGB").save(os.path.join(OUT, f"ad2-{name}.jpg"), quality=90)
        return
    bounds = [0, T_INK, T_WATER, T_END, TOTAL]
    parts = []
    for k in range(len(bounds) - 1):
        path = os.path.join(OUT, f"ad2-part{k}.mp4")
        sc = Scene(path)
        for fi in range(int(round(bounds[k] * FPS)), int(round(bounds[k + 1] * FPS))):
            sc.add(frame(fi / FPS))
        sc.close()
        parts.append(path)
        print("scene", k, flush=True)
    concat(parts, os.path.join(OUT, "ad2-video.mp4"))

    tr = audio.Track(TOTAL)
    tr.sfx("classic_tick", 0.3, 0.6)
    tr.sfx("cosmos_chime", 0.45, 0.4)
    # Ink: the actual tab notes, high to low (Hirajoshi), on each tab crossing.
    hira = [1.784, 1.684, 1.5, 1.191, 1.124, 0.892, 0.842, 0.75]
    a, b = INK_SWIPE
    for k in range(TABS):
        at = T_INK + (a - INK_IN) + (k + 0.5) / TABS * (b - a)
        tr.sfx("ink_pluck", at, 0.7, pan=0.25, rate=hira[k])
    tr.sfx("ink_brush", T_INK + 0.15, 0.5)
    # Water: soft open, a run of bubbles while the water rises.
    tr.sfx("water_soft", T_WATER + PULL[0] - WATER_IN, 0.7)
    for k, r in enumerate([1.5, 1.125, 1.25, 1.875, 0.9375, 1.6875]):
        tr.sfx("water_bubble", T_WATER + PULL[0] - WATER_IN + 0.1 + k * 0.11, 0.5, pan=(-0.4 if k % 2 else 0.4), rate=r)
    tr.sfx("water_drop", T_END + 0.05, 0.6)
    tr.sfx("cosmos_chime", T_END + 0.9, 0.4)
    # A quiet bed: no drums, so the interface sounds lead.
    bed = audio.music_bed(TOTAL, 84, [["D3", "F3", "A3", "C4"], ["A#2", "D3", "F3", "A3"]], start_drums=TOTAL, intensity=0.6,
                          riser_at=T_END - 0.8, impact_at=T_END)
    wav = os.path.join(OUT, "ad2-audio.wav")
    tr.save(wav, bed, music_gain=0.55)
    mux(os.path.join(OUT, "ad2-video.mp4"), wav, os.path.join(OUT, "Cascade-ad-sound-on.mp4"))
    print("done")


if __name__ == "__main__":
    render()
